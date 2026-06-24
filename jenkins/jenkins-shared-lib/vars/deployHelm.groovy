// vars/deployHelm.groovy
// Opinionated Helm deploy pipeline. Provides the full pipeline
// (checkout → lint → optional approval → upgrade → verify) so service
// repos only need a one-line Jenkinsfile.
//
// Required: chart, releaseName, namespace
// Optional: valuesFile, timeout, approve

def call(Map config = [:]) {

    ['chart', 'releaseName', 'namespace'].each { p ->
        if (!config[p]) { error("deployHelm: missing required parameter '${p}'") }
    }

    def valuesFile = config.valuesFile ?: 'values.yaml'
    def timeout    = config.timeout    ?: '5m'
    def approve    = config.containsKey('approve') ? config.approve
                                                   : (config.namespace == 'prod')

    pipeline {
        agent any

        options {
            timeout(time: 30, unit: 'MINUTES')
            ansiColor('xterm')
            buildDiscarder(logRotator(numToKeepStr: '20'))
        }

        stages {
            stage('Checkout') {
                steps { checkout scm }
            }

            stage('Helm lint') {
                steps { sh "helm lint ${config.chart} -f ${valuesFile}" }
            }

            stage('Approve prod deploy') {
                when { expression { approve } }
                steps {
                    input message: "Deploy ${config.releaseName} → ${config.namespace}?",
                          ok: 'Deploy'
                }
            }

            stage('Helm upgrade --install') {
                steps {
                    sh """
                        helm upgrade --install ${config.releaseName} ${config.chart} \\
                            --namespace ${config.namespace} --create-namespace \\
                            --values ${valuesFile} \\
                            --atomic --wait --timeout ${timeout}
                    """
                }
            }

            stage('Verify rollout') {
                steps {
                    sh "kubectl rollout status deploy/${config.releaseName} -n ${config.namespace} --timeout=${timeout}"
                }
            }
        }

        post {
            success { echo "✅ ${config.releaseName} deployed to ${config.namespace}" }
            failure {
                echo "❌ Deploy failed — --atomic should have rolled back"
                sh "helm history ${config.releaseName} -n ${config.namespace} || true"
            }
        }
    }
}

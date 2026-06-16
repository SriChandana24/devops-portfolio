# Kubernetes HPA Hands-On Experiment

Deploy a CPU-sensitive pod, attach an HPA targeting 50% CPU utilization,
generate load from a busybox client, and watch horizontal scaling happen
in real time.

## Prerequisites

- A running cluster (minikube, kind, EKS, GKE)
- `metrics-server` installed — **HPA cannot read CPU/memory without it**

Verify with:

```bash
kubectl top nodes
```

If that errors out, install metrics-server:

```bash
# minikube
minikube addons enable metrics-server

# EKS / generic
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

## Architecture

```
┌─────────────┐
│  load-gen   │  busybox pod running a wget loop
└──────┬──────┘
       │ HTTP
       ▼
┌─────────────┐
│  Service    │  hpa-demo:80 (ClusterIP)
└──────┬──────┘
       │ round-robin
       ▼
┌─────────────────────────────────┐
│  Deployment: hpa-demo           │  CPU-intensive on each request
│  ┌──┐ ┌──┐ ┌──┐ ┌──┐ ...        │
│  │P1│ │P2│ │P3│ │P4│            │  scales 1 → 10 replicas
│  └──┘ └──┘ └──┘ └──┘            │
└─────────────┬───────────────────┘
              │ CPU usage scraped by metrics-server
              ▼
┌─────────────────────────────────┐
│  HPA controller                 │
│  target: avg CPU = 50%          │
│  range:  1 – 10 replicas        │
└─────────────────────────────────┘
```

## Step 1 — Deploy the app

Using `registry.k8s.io/hpa-example` (the canonical HPA demo image — PHP
running a CPU-intensive computation on each request). It makes scaling
behaviour obvious. Plain nginx works too but requires much heavier traffic
to actually spike CPU.

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hpa-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: hpa-demo
  template:
    metadata:
      labels:
        app: hpa-demo
    spec:
      containers:
        - name: hpa-demo
          image: registry.k8s.io/hpa-example
          ports:
            - containerPort: 80
          resources:
            requests:
              cpu: 200m       # HPA math depends on this
              memory: 64Mi
            limits:
              cpu: 500m
              memory: 128Mi
---
apiVersion: v1
kind: Service
metadata:
  name: hpa-demo
spec:
  selector:
    app: hpa-demo
  ports:
    - port: 80
      targetPort: 80
```

```bash
kubectl apply -f deployment.yaml
kubectl get pods -l app=hpa-demo
```

> **Why `requests.cpu` matters:** HPA computes utilization as
> `actualUsage / requestedAmount`. With no CPU request set, HPA cannot
> scale on CPU at all and `kubectl get hpa` will show `<unknown>`.

## Step 2 — Create the HPA

```yaml
# hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: hpa-demo
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: hpa-demo
  minReplicas: 1
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50
```

```bash
kubectl apply -f hpa.yaml
kubectl get hpa hpa-demo
```

The shortcut form does exactly the same thing:

```bash
kubectl autoscale deployment hpa-demo --cpu-percent=50 --min=1 --max=10
```

`TARGETS` shows `<unknown>/50%` for the first few seconds while
metrics-server collects its first sample, then settles around `1%/50%`.

## Step 3 — Generate load

In a second terminal:

```bash
kubectl run -i --tty load-gen --image=busybox --rm -- /bin/sh
```

Inside the pod:

```sh
while true; do wget -q -O- http://hpa-demo; done
```

The service is reachable by name (`hpa-demo`) from any pod in the same
namespace via cluster DNS.

## Step 4 — Watch it scale

In a third terminal, leave these running:

```bash
# HPA status — the main view
kubectl get hpa hpa-demo --watch

# Pod count
kubectl get pods -l app=hpa-demo --watch

# Live CPU per pod
watch -n 2 kubectl top pods -l app=hpa-demo
```

### Expected progression

HPA formula: `desired = ceil(currentReplicas × currentMetric / targetMetric)`

## Cleanup

```bash
kubectl delete hpa hpa-demo
kubectl delete deployment hpa-demo
kubectl delete service hpa-demo
# If load-gen is still hanging:
kubectl delete pod load-gen --force --grace-period=0
```

## Interview talking points

1. **HPA needs `resources.requests`.** Without a CPU request, HPA can't
   compute utilization and shows `<unknown>`. Classic interview gotcha —
   the right diagnostic question is "what do the requests look like?"

2. **Scale-up is fast (~30s), scale-down is slow (~5 min).** Asymmetric
   on purpose. `--horizontal-pod-autoscaler-downscale-stabilization`
   defaults to 5 minutes to prevent flapping under bursty load.

3. **The HPA formula:** `desired = ceil(currentReplicas × currentMetric / targetMetric)`.
   Worth memorizing — it's a common whiteboard question.

4. **metrics-server is a prerequisite.** Not installed by default on
   minikube, EKS, or kind. `kubectl top` failing is the immediate giveaway.

5. **`autoscaling/v2` supports multiple metrics.** CPU + memory + custom
   metrics simultaneously. HPA computes a desired replica count for each
   and picks the maximum.

6. **HPA vs VPA conflict.** Both adjusting CPU on the same workload will
   fight each other. Safe pattern: HPA on CPU, VPA on memory; or VPA in
   `recommendation-only` mode while HPA handles all scaling.

7. **HPA scales pods, Cluster Autoscaler scales nodes.** When HPA wants
   more pods than fit on existing nodes, pods stay `Pending`, and CA adds
   nodes. HPA + CA = elastic infrastructure.

8. **Production: use `behavior` policies.** The v2 API lets you cap scale
   speed — e.g. "no more than 4 pods added per minute" or "no more than
   10% removed per minute" — to avoid thrashing.

## Going further

- Switch to a custom metric (HTTP RPS via Prometheus Adapter)
- Add Cluster Autoscaler on the node group and watch nodes appear too
- Try KEDA for event-driven scaling on queue depth or Kafka lag
- Experiment with the `behavior` block — aggressive vs gentle policies

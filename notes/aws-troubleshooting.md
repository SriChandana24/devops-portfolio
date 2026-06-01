**EC2 can't reach internet" debug checklist 
**

Route table: private subnet → 0.0.0.0/0 → NAT Gateway?
NAT Gateway in Available state?
NAT Gateway in a public subnet (not private)?
NAT Gateway has an Elastic IP attached?
Public subnet route table → 0.0.0.0/0 → IGW?
Security Group: outbound rules allow egress?
NACL (stateless!): both inbound AND outbound rules allow return traffic on ephemeral ports (1024–65535)?
VPC has enableDnsHostnames + enableDnsSupport if you're using domain names?
Inside EC2: iptables rules, OS-level firewall?
Test with curl -v http://example.com or dig to see where it actually fails.

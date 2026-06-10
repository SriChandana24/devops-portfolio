# Custom VPC with Public/Private Subnets and an S3 Gateway Endpoint

A Terraform configuration that provisions a custom AWS VPC with public and
private subnets, an Internet Gateway, a NAT Gateway, and a **Gateway VPC
Endpoint for S3**. A private EC2 instance (with an IAM role for S3 read access)
demonstrates that S3 traffic stays on the AWS network and never traverses — or
gets billed by — the NAT Gateway.

## Architecture

```
                          ┌──────────────────────────────────────────────┐
                          │   VPC  10.0.0.0/16                            │
   Internet ──► IGW ──────┼──► Public RT ──► Public Subnet (10.0.1.0/24)  │
                          │                      │                        │
                          │                      └──► NAT Gateway + EIP    │
                          │                             │                  │
                          │      Private RT ◄────────────┘ (0.0.0.0/0)     │
                          │           │                                    │
                          │           ├─► Private Subnet (10.0.2.0/24)     │
                          │           │       └─► EC2 (IAM: S3 read)       │
                          │           │                                    │
                          │           └─► S3 prefix list ──► S3 Gateway Endpoint ──► S3
                          └──────────────────────────────────────────────┘
                                          (stays on the AWS backbone)
```

| Resource | Detail | Purpose |
|---|---|---|
| VPC | `10.0.0.0/16` | Network boundary |
| Public subnet | `10.0.1.0/24` | Internet-facing; auto-assigns public IPs |
| Private subnet | `10.0.2.0/24` | Internal resources; no public IPs |
| Internet Gateway | — | Direct internet access for the public subnet |
| NAT Gateway | In public subnet | Outbound internet for the private subnet |
| Elastic IP | Attached to NAT | Static public IP for the NAT Gateway |
| **S3 Gateway Endpoint** | Associated with private RT | Routes S3 traffic privately, off the NAT path |
| EC2 instance | In private subnet, `t3.micro` | Test client with an IAM role for S3 read |
| IAM role | S3 read-only + SSM core | Lets the instance read S3 and connect via Session Manager |

## The auto-added route

Associating the gateway endpoint with the private route table causes AWS to
**automatically inject** a route — you never write it yourself:

| Destination | Target |
|---|---|
| `10.0.0.0/16` | local |
| `pl-xxxxxxxx` (S3 managed prefix list) | `vpce-xxxxxxxx` (the endpoint) |
| `0.0.0.0/0` | `nat-xxxxxxxx` |

Because the S3 prefix list is more specific than `0.0.0.0/0`, longest-prefix
match sends **all** S3 traffic to the endpoint instead of the NAT Gateway.

## Files

| File | Contents |
|---|---|
| `providers.tf` | Terraform + AWS provider configuration |
| `main.tf` | VPC, subnets, IGW, NAT, route tables, S3 endpoint, IAM role, EC2 |
| `outputs.tf` | VPC/subnet IDs, NAT IP, endpoint ID, EC2 instance ID |
| `verification.png` | Screenshot of `aws s3 ls` + the route table proving the bypass |

## Usage

```bash
terraform init
terraform plan
terraform apply     # type "yes"
```

### Verify the private path

```bash
aws ssm start-session --target <ec2_instance_id>   # connect to the private instance
aws s3 ls                                          # succeeds via the gateway endpoint
```

Confirm the bypass by checking the private route table for the
`pl-xxxx → vpce-xxxx` entry, or watching that the NAT Gateway's
`BytesOutToDestination` metric does not move during the `aws s3 ls`.

## Teardown

```bash
terraform destroy   # type "yes"
```

> ⚠️ **Cost warning:** The NAT Gateway (~$0.045/hr, ~$32/month) and the
> `t3.micro` EC2 instance are billed while running. Destroy this stack as soon
> as the exercise is done, then verify in the AWS Console (VPC, EC2, and
> Elastic IP sections) that nothing is left behind. The S3 gateway endpoint
> itself is free.

## Why this saves money

Gateway VPC Endpoints for S3 are one of the few "free wins" in AWS networking:

- **The endpoint costs nothing.** Gateway endpoints (S3 and DynamoDB) have **no
  hourly charge and no per-GB data processing charge.** They are free to create
  and use.
- **They cut NAT Gateway data charges.** Without the endpoint, S3 traffic from
  a private subnet goes out through the NAT Gateway, which bills **~$0.045 per
  GB processed** on top of its hourly cost. Routing S3 traffic through the
  gateway endpoint avoids that per-GB charge entirely.
- **The savings scale with data volume.** Anything that moves a lot of data to
  or from S3 — backups, data-lake reads, log shipping, pulling artifacts — adds
  up fast:

  | S3 data through NAT / month | NAT data-processing cost | With gateway endpoint |
  |---|---|---|
  | 100 GB | ~$4.50 | **$0** |
  | 1 TB | ~$45 | **$0** |
  | 10 TB | ~$450 | **$0** |

- **Bonus: better security posture.** Traffic to S3 never leaves the AWS
  network, and you can attach an endpoint policy to restrict which buckets the
  VPC can reach.

The trade-off to remember: gateway endpoints only cover S3 and DynamoDB, only
work from within the VPC (not on-prem over VPN/Direct Connect), and route by
prefix list — so they reduce NAT usage for S3, but you still need NAT (or
interface endpoints) for other internet-bound traffic.

## Notes

- The NAT Gateway depends on the Internet Gateway; Terraform orders this
  automatically via `depends_on`.
- The EC2 instance has no public IP and no SSH key — connection is via SSM
  Session Manager, enabled by the `AmazonSSMManagedInstanceCore` policy.
- The `0.0.0.0/0` routes are the "default route to the internet" you would
  otherwise add by hand in the console.

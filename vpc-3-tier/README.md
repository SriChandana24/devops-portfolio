# Custom VPC with Public/Private Subnets

A Terraform configuration that provisions a custom AWS VPC with a public and
private subnet, an Internet Gateway, and a NAT Gateway, with route tables wired
up so the public subnet reaches the internet directly and the private subnet
reaches it through NAT.

## Architecture

```
                          ┌─────────────────────────────┐
                          │   VPC  10.0.0.0/16           │
                          │                             │
   Internet ──► IGW ──────┼──► Public RT ──► Public Subnet (10.0.1.0/24)
                          │                      │
                          │                      └──► NAT Gateway + EIP
                          │                             │
                          │      Private RT ◄───────────┘
                          │           │
                          │           └──► Private Subnet (10.0.2.0/24) ──► Internet (outbound only)
                          └─────────────────────────────┘
```

| Resource | CIDR / Detail | Purpose |
|---|---|---|
| VPC | `10.0.0.0/16` | Network boundary |
| Public subnet | `10.0.1.0/24` | Internet-facing resources; auto-assigns public IPs |
| Private subnet | `10.0.2.0/24` | Internal resources; outbound internet via NAT only |
| Internet Gateway | — | Direct internet access for the public subnet |
| NAT Gateway | In public subnet | Outbound internet for the private subnet |
| Elastic IP | Attached to NAT | Static public IP for the NAT Gateway |
| Public route table | `0.0.0.0/0` → IGW | Routes public subnet traffic to the internet |
| Private route table | `0.0.0.0/0` → NAT | Routes private subnet traffic through NAT |

## Files

| File | Contents |
|---|---|
| `providers.tf` | Terraform + AWS provider configuration |
| `main.tf` | VPC, subnets, IGW, NAT Gateway, EIP, route tables, associations |
| `outputs.tf` | VPC ID, subnet IDs, and the NAT Gateway public IP |

## Prerequisites

- [Terraform](https://developer.hashicorp.com/terraform/install) installed (`terraform -version`)
- AWS CLI configured with credentials (`aws configure`)

## Usage

```bash
terraform init      # download providers and initialize
terraform plan      # preview the changes
terraform apply     # create the resources (type "yes" to confirm)
```

## Teardown

```bash
terraform destroy   # remove everything (type "yes" to confirm)
```

> ⚠️ **Cost warning:** Unlike a plain S3 bucket, this stack is **not** free.
> The NAT Gateway costs roughly **$0.045/hour (~$32/month)** plus data
> processing charges, and the Elastic IP is billed while attached to it.
> Run `terraform destroy` as soon as the exercise is finished, then confirm in
> the AWS Console (VPC and Elastic IP sections) that nothing is left running.

## Notes

- S3 bucket names and many networking resources must be unique within a region/account.
- The NAT Gateway depends on the Internet Gateway; Terraform handles this ordering automatically via `depends_on`.
- The `0.0.0.0/0` routes are the "default route to the internet" you would otherwise add by hand in the console.

output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_id" {
  value = aws_subnet.public.id
}

output "private_subnet_id" {
  value = aws_subnet.private.id
}

output "nat_gateway_ip" {
  value = aws_eip.nat.public_ip
}

output "s3_endpoint_id" {
  value = aws_vpc_endpoint.s3.id
}

output "ec2_instance_id" {
  value = aws_instance.private.id
}
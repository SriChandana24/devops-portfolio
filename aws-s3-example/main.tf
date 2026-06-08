resource "random_id" "suffix" {
  byte_length = 4
}

resource "aws_s3_bucket" "example" {
  bucket = "tf-learn-example-${random_id.suffix.hex}"

  tags = {
    Name      = "tf-learn-example"
    ManagedBy = "Terraform"
    project   = "project1"
  }
}

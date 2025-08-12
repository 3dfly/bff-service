#!/bin/bash

# AWS Setup Script for BFF Service
# This script sets up all required AWS infrastructure

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 Setting up AWS infrastructure for BFF Service${NC}"

# Configuration
PROJECT_NAME="threedfly-bff"
AWS_REGION="${AWS_REGION:-us-east-1}"
CLUSTER_NAME="${PROJECT_NAME}-cluster"
SERVICE_NAME="${PROJECT_NAME}-service"
TASK_FAMILY="${PROJECT_NAME}-task"
ECR_REPO="${PROJECT_NAME}-repo"

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo -e "${RED}❌ AWS CLI is not installed. Please install it first.${NC}"
    exit 1
fi

# Check if AWS credentials are configured
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}❌ AWS credentials not configured. Please run 'aws configure' first.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ AWS CLI and credentials verified${NC}"

# Function to check if resource exists
resource_exists() {
    local resource_type=$1
    local resource_name=$2
    
    case $resource_type in
        "ecr")
            aws ecr describe-repositories --repository-names "$resource_name" --region "$AWS_REGION" &> /dev/null
            ;;
        "ecs-cluster")
            aws ecs describe-clusters --clusters "$resource_name" --region "$AWS_REGION" --query 'clusters[0].status' --output text 2> /dev/null | grep -q "ACTIVE"
            ;;
        "iam-role")
            aws iam get-role --role-name "$resource_name" &> /dev/null
            ;;
        "log-group")
            aws logs describe-log-groups --log-group-name-prefix "$resource_name" --region "$AWS_REGION" --query 'logGroups[0]' --output text | grep -q "$resource_name"
            ;;
    esac
}

# 1. Create ECR Repository
echo -e "${YELLOW}📦 Setting up ECR Repository...${NC}"
if resource_exists "ecr" "$ECR_REPO"; then
    echo -e "${GREEN}✅ ECR repository '$ECR_REPO' already exists${NC}"
else
    aws ecr create-repository \
        --repository-name "$ECR_REPO" \
        --region "$AWS_REGION" \
        --image-scanning-configuration scanOnPush=true \
        --lifecycle-policy-text '{
            "rules": [
                {
                    "rulePriority": 1,
                    "selection": {
                        "tagStatus": "untagged",
                        "countType": "sinceImagePushed",
                        "countUnit": "days",
                        "countNumber": 7
                    },
                    "action": {
                        "type": "expire"
                    }
                },
                {
                    "rulePriority": 2,
                    "selection": {
                        "tagStatus": "tagged",
                        "countType": "imageCountMoreThan",
                        "countNumber": 10
                    },
                    "action": {
                        "type": "expire"
                    }
                }
            ]
        }'
    echo -e "${GREEN}✅ ECR repository '$ECR_REPO' created${NC}"
fi

# Get ECR login token and login
echo -e "${YELLOW}🔐 Logging into ECR...${NC}"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com"
echo -e "${GREEN}✅ ECR login successful${NC}"

# 2. Create IAM Role for ECS Task
echo -e "${YELLOW}🔑 Setting up IAM roles...${NC}"
TASK_ROLE_NAME="${PROJECT_NAME}-task-role"
EXECUTION_ROLE_NAME="${PROJECT_NAME}-execution-role"

# Create task role if it doesn't exist
if resource_exists "iam-role" "$TASK_ROLE_NAME"; then
    echo -e "${GREEN}✅ IAM task role '$TASK_ROLE_NAME' already exists${NC}"
else
    aws iam create-role \
        --role-name "$TASK_ROLE_NAME" \
        --assume-role-policy-document '{
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": {
                        "Service": "ecs-tasks.amazonaws.com"
                    },
                    "Action": "sts:AssumeRole"
                }
            ]
        }'
    
    # Attach policies for application functionality
    aws iam attach-role-policy \
        --role-name "$TASK_ROLE_NAME" \
        --policy-arn "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
    
    echo -e "${GREEN}✅ IAM task role '$TASK_ROLE_NAME' created${NC}"
fi

# Create execution role if it doesn't exist
if resource_exists "iam-role" "$EXECUTION_ROLE_NAME"; then
    echo -e "${GREEN}✅ IAM execution role '$EXECUTION_ROLE_NAME' already exists${NC}"
else
    aws iam create-role \
        --role-name "$EXECUTION_ROLE_NAME" \
        --assume-role-policy-document '{
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": {
                        "Service": "ecs-tasks.amazonaws.com"
                    },
                    "Action": "sts:AssumeRole"
                }
            ]
        }'
    
    # Attach execution role policy
    aws iam attach-role-policy \
        --role-name "$EXECUTION_ROLE_NAME" \
        --policy-arn "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
    
    echo -e "${GREEN}✅ IAM execution role '$EXECUTION_ROLE_NAME' created${NC}"
fi

# 3. Create CloudWatch Log Group
echo -e "${YELLOW}📝 Setting up CloudWatch Log Group...${NC}"
LOG_GROUP_NAME="/ecs/$PROJECT_NAME"
if resource_exists "log-group" "$LOG_GROUP_NAME"; then
    echo -e "${GREEN}✅ CloudWatch log group '$LOG_GROUP_NAME' already exists${NC}"
else
    aws logs create-log-group \
        --log-group-name "$LOG_GROUP_NAME" \
        --region "$AWS_REGION"
    
    # Set retention policy
    aws logs put-retention-policy \
        --log-group-name "$LOG_GROUP_NAME" \
        --retention-in-days 30 \
        --region "$AWS_REGION"
    
    echo -e "${GREEN}✅ CloudWatch log group '$LOG_GROUP_NAME' created${NC}"
fi

# 4. Create ECS Cluster
echo -e "${YELLOW}🏗️ Setting up ECS Cluster...${NC}"
if resource_exists "ecs-cluster" "$CLUSTER_NAME"; then
    echo -e "${GREEN}✅ ECS cluster '$CLUSTER_NAME' already exists${NC}"
else
    aws ecs create-cluster \
        --cluster-name "$CLUSTER_NAME" \
        --capacity-providers FARGATE EC2 \
        --default-capacity-provider-strategy capacityProvider=FARGATE,weight=1 \
        --region "$AWS_REGION" \
        --tags key=Project,value="$PROJECT_NAME" key=Environment,value=production
    
    echo -e "${GREEN}✅ ECS cluster '$CLUSTER_NAME' created${NC}"
fi

# 5. Get VPC and Subnet information
echo -e "${YELLOW}🌐 Getting VPC information...${NC}"
VPC_ID=$(aws ec2 describe-vpcs --filters "Name=is-default,Values=true" --query 'Vpcs[0].VpcId' --output text --region "$AWS_REGION")
SUBNET_IDS=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" --query 'Subnets[].SubnetId' --output text --region "$AWS_REGION")
SUBNET_ID_ARRAY=($SUBNET_IDS)

echo -e "${GREEN}✅ Using VPC: $VPC_ID${NC}"
echo -e "${GREEN}✅ Using Subnets: ${SUBNET_ID_ARRAY[*]}${NC}"

# 6. Create Security Group
echo -e "${YELLOW}🔒 Setting up Security Group...${NC}"
SG_NAME="${PROJECT_NAME}-sg"
SG_ID=$(aws ec2 describe-security-groups --filters "Name=group-name,Values=$SG_NAME" --query 'SecurityGroups[0].GroupId' --output text --region "$AWS_REGION" 2>/dev/null)

if [[ "$SG_ID" == "None" ]] || [[ -z "$SG_ID" ]]; then
    SG_ID=$(aws ec2 create-security-group \
        --group-name "$SG_NAME" \
        --description "Security group for $PROJECT_NAME" \
        --vpc-id "$VPC_ID" \
        --region "$AWS_REGION" \
        --query 'GroupId' \
        --output text)
    
    # Allow HTTP traffic on port 8082
    aws ec2 authorize-security-group-ingress \
        --group-id "$SG_ID" \
        --protocol tcp \
        --port 8082 \
        --cidr 0.0.0.0/0 \
        --region "$AWS_REGION"
    
    # Allow HTTPS traffic
    aws ec2 authorize-security-group-ingress \
        --group-id "$SG_ID" \
        --protocol tcp \
        --port 443 \
        --cidr 0.0.0.0/0 \
        --region "$AWS_REGION"
    
    echo -e "${GREEN}✅ Security group '$SG_NAME' created with ID: $SG_ID${NC}"
else
    echo -e "${GREEN}✅ Security group '$SG_NAME' already exists with ID: $SG_ID${NC}"
fi

# 7. Save configuration for deployment scripts
echo -e "${YELLOW}💾 Saving configuration...${NC}"
cat > "aws-config.env" << EOF
# AWS Configuration for BFF Service
export AWS_REGION="$AWS_REGION"
export AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
export PROJECT_NAME="$PROJECT_NAME"
export CLUSTER_NAME="$CLUSTER_NAME"
export SERVICE_NAME="$SERVICE_NAME"
export TASK_FAMILY="$TASK_FAMILY"
export ECR_REPO="$ECR_REPO"
export ECR_URI="\$AWS_ACCOUNT_ID.dkr.ecr.\$AWS_REGION.amazonaws.com/\$ECR_REPO"
export VPC_ID="$VPC_ID"
export SUBNET_IDS="$SUBNET_IDS"
export SECURITY_GROUP_ID="$SG_ID"
export TASK_ROLE_ARN="arn:aws:iam::\$AWS_ACCOUNT_ID:role/$TASK_ROLE_NAME"
export EXECUTION_ROLE_ARN="arn:aws:iam::\$AWS_ACCOUNT_ID:role/$EXECUTION_ROLE_NAME"
export LOG_GROUP_NAME="$LOG_GROUP_NAME"
EOF

echo -e "${GREEN}✅ Configuration saved to aws-config.env${NC}"

# 8. Create task definition template
echo -e "${YELLOW}📋 Creating ECS Task Definition...${NC}"
cat > "task-definition.json" << EOF
{
    "family": "$TASK_FAMILY",
    "networkMode": "awsvpc",
    "requiresCompatibilities": ["FARGATE"],
    "cpu": "512",
    "memory": "1024",
    "executionRoleArn": "arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):role/$EXECUTION_ROLE_NAME",
    "taskRoleArn": "arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):role/$TASK_ROLE_NAME",
    "containerDefinitions": [
        {
            "name": "$PROJECT_NAME",
            "image": "$(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO:latest",
            "portMappings": [
                {
                    "containerPort": 8082,
                    "protocol": "tcp"
                }
            ],
            "essential": true,
            "environment": [
                {
                    "name": "SPRING_PROFILES_ACTIVE",
                    "value": "production"
                },
                {
                    "name": "ORDER_SERVICE_URL",
                    "value": "http://order-service:8080"
                },
                {
                    "name": "PRODUCT_SERVICE_URL",
                    "value": "http://product-service:8081"
                }
            ],
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "$LOG_GROUP_NAME",
                    "awslogs-region": "$AWS_REGION",
                    "awslogs-stream-prefix": "ecs"
                }
            },
            "healthCheck": {
                "command": [
                    "CMD-SHELL",
                    "curl -f http://localhost:8082/api/orders/health || exit 1"
                ],
                "interval": 30,
                "timeout": 5,
                "retries": 3,
                "startPeriod": 60
            }
        }
    ]
}
EOF

echo -e "${GREEN}✅ Task definition created${NC}"

echo -e "${GREEN}🎉 AWS infrastructure setup completed!${NC}"
echo -e "${BLUE}📝 Next steps:${NC}"
echo -e "1. Build and push Docker image: ${YELLOW}./scripts/build-and-deploy.sh${NC}"
echo -e "2. Deploy the service: ${YELLOW}./scripts/deploy.sh${NC}"
echo -e "3. Monitor logs: ${YELLOW}./scripts/view-logs.sh${NC}"

echo -e "${BLUE}📋 Configuration saved in:${NC}"
echo -e "  - aws-config.env (environment variables)"
echo -e "  - task-definition.json (ECS task definition)"

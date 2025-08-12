#!/bin/bash

# Build and Deploy Script for BFF Service
# This script builds the Docker image and pushes it to ECR

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔨 Building and deploying BFF Service to AWS${NC}"

# Load configuration
if [[ -f "aws-config.env" ]]; then
    source aws-config.env
    echo -e "${GREEN}✅ Configuration loaded${NC}"
else
    echo -e "${RED}❌ Configuration file 'aws-config.env' not found. Please run setup-aws.sh first.${NC}"
    exit 1
fi

# Check if Docker is running
if ! docker info &> /dev/null; then
    echo -e "${RED}❌ Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

# Build version tag
BUILD_VERSION="${BUILD_VERSION:-$(date +%Y%m%d-%H%M%S)}"
LATEST_TAG="$ECR_URI:latest"
VERSION_TAG="$ECR_URI:$BUILD_VERSION"

echo -e "${YELLOW}🏗️ Building Docker image...${NC}"
echo -e "  Latest tag: $LATEST_TAG"
echo -e "  Version tag: $VERSION_TAG"

# Build the Docker image
docker build -t "$PROJECT_NAME" .
docker tag "$PROJECT_NAME" "$LATEST_TAG"
docker tag "$PROJECT_NAME" "$VERSION_TAG"

echo -e "${GREEN}✅ Docker image built successfully${NC}"

# Login to ECR
echo -e "${YELLOW}🔐 Logging into ECR...${NC}"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"

# Push images to ECR
echo -e "${YELLOW}📤 Pushing images to ECR...${NC}"
docker push "$LATEST_TAG"
docker push "$VERSION_TAG"

echo -e "${GREEN}✅ Images pushed to ECR successfully${NC}"

# Update task definition with new image
echo -e "${YELLOW}📋 Updating ECS task definition...${NC}"

# Create updated task definition
UPDATED_TASK_DEF=$(cat task-definition.json | jq --arg IMAGE "$LATEST_TAG" '.containerDefinitions[0].image = $IMAGE')

# Register new task definition
TASK_DEFINITION_ARN=$(echo "$UPDATED_TASK_DEF" | aws ecs register-task-definition \
    --region "$AWS_REGION" \
    --cli-input-json file:///dev/stdin \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

echo -e "${GREEN}✅ Task definition registered: $TASK_DEFINITION_ARN${NC}"

# Check if service exists
SERVICE_EXISTS=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[0].status' \
    --output text 2>/dev/null || echo "None")

if [[ "$SERVICE_EXISTS" == "ACTIVE" ]]; then
    echo -e "${YELLOW}🔄 Updating existing ECS service...${NC}"
    
    # Update existing service
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "$SERVICE_NAME" \
        --task-definition "$TASK_DEFINITION_ARN" \
        --region "$AWS_REGION" \
        --query 'service.serviceName' \
        --output text
    
    echo -e "${GREEN}✅ Service updated successfully${NC}"
    
    # Wait for deployment to complete
    echo -e "${YELLOW}⏳ Waiting for deployment to complete...${NC}"
    aws ecs wait services-stable \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$AWS_REGION"
    
    echo -e "${GREEN}✅ Deployment completed successfully${NC}"
    
else
    echo -e "${YELLOW}🚀 Creating new ECS service...${NC}"
    
    # Parse subnet IDs into array for JSON
    SUBNET_ARRAY=""
    for subnet in $SUBNET_IDS; do
        if [[ -z "$SUBNET_ARRAY" ]]; then
            SUBNET_ARRAY="\"$subnet\""
        else
            SUBNET_ARRAY="$SUBNET_ARRAY,\"$subnet\""
        fi
    done
    
    # Create new service
    aws ecs create-service \
        --cluster "$CLUSTER_NAME" \
        --service-name "$SERVICE_NAME" \
        --task-definition "$TASK_DEFINITION_ARN" \
        --desired-count 1 \
        --launch-type FARGATE \
        --platform-version LATEST \
        --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_ARRAY],securityGroups=[$SECURITY_GROUP_ID],assignPublicIp=ENABLED}" \
        --enable-execute-command \
        --tags key=Project,value="$PROJECT_NAME" key=Environment,value=production \
        --region "$AWS_REGION" \
        --query 'service.serviceName' \
        --output text
    
    echo -e "${GREEN}✅ Service created successfully${NC}"
    
    # Wait for service to become stable
    echo -e "${YELLOW}⏳ Waiting for service to start...${NC}"
    aws ecs wait services-stable \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$AWS_REGION"
    
    echo -e "${GREEN}✅ Service is running successfully${NC}"
fi

# Get service information
echo -e "${YELLOW}📊 Getting service information...${NC}"
TASK_ARN=$(aws ecs list-tasks \
    --cluster "$CLUSTER_NAME" \
    --service-name "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'taskArns[0]' \
    --output text)

if [[ "$TASK_ARN" != "None" ]] && [[ -n "$TASK_ARN" ]]; then
    PUBLIC_IP=$(aws ecs describe-tasks \
        --cluster "$CLUSTER_NAME" \
        --tasks "$TASK_ARN" \
        --region "$AWS_REGION" \
        --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' \
        --output text | xargs -I {} aws ec2 describe-network-interfaces \
        --network-interface-ids {} \
        --region "$AWS_REGION" \
        --query 'NetworkInterfaces[0].Association.PublicIp' \
        --output text)
    
    if [[ "$PUBLIC_IP" != "None" ]] && [[ -n "$PUBLIC_IP" ]]; then
        echo -e "${GREEN}✅ Service is accessible at: http://$PUBLIC_IP:8082${NC}"
        echo -e "${GREEN}🏥 Health check: http://$PUBLIC_IP:8082/api/orders/health${NC}"
        echo -e "${GREEN}📚 API Documentation: http://$PUBLIC_IP:8082/swagger-ui.html${NC}"
    fi
fi

# Save deployment information
echo -e "${YELLOW}💾 Saving deployment information...${NC}"
cat > "deployment-info.env" << EOF
# Deployment Information
export DEPLOYED_VERSION="$BUILD_VERSION"
export DEPLOYED_IMAGE="$LATEST_TAG"
export TASK_DEFINITION_ARN="$TASK_DEFINITION_ARN"
export DEPLOYMENT_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
EOF

echo -e "${GREEN}🎉 Build and deployment completed successfully!${NC}"
echo -e "${BLUE}📝 Next steps:${NC}"
echo -e "1. Check service health: ${YELLOW}./scripts/health-check.sh${NC}"
echo -e "2. View logs: ${YELLOW}./scripts/view-logs.sh${NC}"
echo -e "3. Test the API: ${YELLOW}./scripts/test-api.sh${NC}"

if [[ -n "$PUBLIC_IP" ]]; then
    echo -e "${BLUE}🌐 Service URLs:${NC}"
    echo -e "  - API Base: http://$PUBLIC_IP:8082/api/orders"
    echo -e "  - Health Check: http://$PUBLIC_IP:8082/api/orders/health"
    echo -e "  - Swagger UI: http://$PUBLIC_IP:8082/swagger-ui.html"
fi

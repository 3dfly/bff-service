#!/bin/bash

# Health Check Script for BFF Service
# This script checks the health of the deployed service

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🏥 Health checking BFF Service${NC}"

# Load configuration
if [[ -f "aws-config.env" ]]; then
    source aws-config.env
    echo -e "${GREEN}✅ Configuration loaded${NC}"
else
    echo -e "${RED}❌ Configuration file 'aws-config.env' not found. Please run setup-aws.sh first.${NC}"
    exit 1
fi

# Function to check HTTP endpoint
check_endpoint() {
    local url=$1
    local description=$2
    
    echo -e "${YELLOW}🔍 Checking $description: $url${NC}"
    
    if curl -f -s --max-time 10 "$url" > /dev/null; then
        echo -e "${GREEN}✅ $description is healthy${NC}"
        return 0
    else
        echo -e "${RED}❌ $description is not responding${NC}"
        return 1
    fi
}

# Function to check endpoint with response
check_endpoint_with_response() {
    local url=$1
    local description=$2
    
    echo -e "${YELLOW}🔍 Checking $description: $url${NC}"
    
    response=$(curl -f -s --max-time 10 "$url" 2>/dev/null || echo "ERROR")
    
    if [[ "$response" != "ERROR" ]]; then
        echo -e "${GREEN}✅ $description is healthy${NC}"
        echo -e "${BLUE}📄 Response: $response${NC}"
        return 0
    else
        echo -e "${RED}❌ $description is not responding${NC}"
        return 1
    fi
}

# Check ECS service status
echo -e "${YELLOW}🔍 Checking ECS service status...${NC}"
SERVICE_STATUS=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[0].status' \
    --output text 2>/dev/null || echo "None")

if [[ "$SERVICE_STATUS" == "ACTIVE" ]]; then
    echo -e "${GREEN}✅ ECS service is ACTIVE${NC}"
    
    # Get running task count
    RUNNING_COUNT=$(aws ecs describe-services \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$AWS_REGION" \
        --query 'services[0].runningCount' \
        --output text)
    
    DESIRED_COUNT=$(aws ecs describe-services \
        --cluster "$CLUSTER_NAME" \
        --services "$SERVICE_NAME" \
        --region "$AWS_REGION" \
        --query 'services[0].desiredCount' \
        --output text)
    
    echo -e "${BLUE}📊 Running tasks: $RUNNING_COUNT/$DESIRED_COUNT${NC}"
    
    if [[ "$RUNNING_COUNT" == "$DESIRED_COUNT" ]] && [[ "$RUNNING_COUNT" -gt 0 ]]; then
        echo -e "${GREEN}✅ All desired tasks are running${NC}"
    else
        echo -e "${YELLOW}⚠️ Task count mismatch or no tasks running${NC}"
    fi
else
    echo -e "${RED}❌ ECS service is not active. Status: $SERVICE_STATUS${NC}"
    exit 1
fi

# Get public IP of the service
echo -e "${YELLOW}🔍 Getting service public IP...${NC}"
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
        --output text 2>/dev/null || echo "None")
    
    if [[ "$PUBLIC_IP" != "None" ]] && [[ -n "$PUBLIC_IP" ]]; then
        echo -e "${GREEN}✅ Service public IP: $PUBLIC_IP${NC}"
        
        # Test HTTP endpoints
        BASE_URL="http://$PUBLIC_IP:8082"
        
        echo -e "${BLUE}🌐 Testing HTTP endpoints...${NC}"
        
        # Health check endpoint
        check_endpoint_with_response "$BASE_URL/api/orders/health" "Health Check"
        
        # Actuator health
        check_endpoint "$BASE_URL/actuator/health" "Actuator Health"
        
        # API docs
        check_endpoint "$BASE_URL/swagger-ui.html" "Swagger UI"
        
        # Metrics endpoint
        check_endpoint "$BASE_URL/actuator/metrics" "Metrics"
        
        # Circuit breaker metrics
        check_endpoint "$BASE_URL/actuator/circuitbreakers" "Circuit Breakers"
        
        echo -e "${GREEN}🎉 Health check completed!${NC}"
        echo -e "${BLUE}🌐 Service URLs:${NC}"
        echo -e "  - API Base: $BASE_URL/api/orders"
        echo -e "  - Health Check: $BASE_URL/api/orders/health"
        echo -e "  - Swagger UI: $BASE_URL/swagger-ui.html"
        echo -e "  - Actuator: $BASE_URL/actuator"
        echo -e "  - Metrics: $BASE_URL/actuator/metrics"
        
    else
        echo -e "${RED}❌ Could not determine public IP${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ No running tasks found${NC}"
    exit 1
fi

# Check CloudWatch logs
echo -e "${YELLOW}🔍 Checking recent logs for errors...${NC}"
RECENT_ERRORS=$(aws logs filter-log-events \
    --log-group-name "$LOG_GROUP_NAME" \
    --region "$AWS_REGION" \
    --start-time "$(date -d '5 minutes ago' +%s)000" \
    --filter-pattern "ERROR" \
    --query 'events[].message' \
    --output text 2>/dev/null || echo "")

if [[ -z "$RECENT_ERRORS" ]]; then
    echo -e "${GREEN}✅ No recent errors found in logs${NC}"
else
    echo -e "${YELLOW}⚠️ Recent errors found in logs:${NC}"
    echo "$RECENT_ERRORS"
fi

echo -e "${GREEN}🏥 Health check completed successfully!${NC}"

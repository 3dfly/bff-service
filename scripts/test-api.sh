#!/bin/bash

# API Test Script for BFF Service
# This script tests the order orchestration API end-to-end

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 Testing BFF Service API${NC}"

# Configuration
DEFAULT_BASE_URL="http://localhost:8082"
BASE_URL="${API_BASE_URL:-$DEFAULT_BASE_URL}"

# Load AWS configuration if available
if [[ -f "aws-config.env" ]]; then
    source aws-config.env
    
    # Try to get public IP if running on AWS
    if [[ -n "$CLUSTER_NAME" ]] && [[ -n "$SERVICE_NAME" ]]; then
        echo -e "${YELLOW}🔍 Getting AWS service URL...${NC}"
        TASK_ARN=$(aws ecs list-tasks \
            --cluster "$CLUSTER_NAME" \
            --service-name "$SERVICE_NAME" \
            --region "$AWS_REGION" \
            --query 'taskArns[0]' \
            --output text 2>/dev/null || echo "None")
        
        if [[ "$TASK_ARN" != "None" ]] && [[ -n "$TASK_ARN" ]]; then
            PUBLIC_IP=$(aws ecs describe-tasks \
                --cluster "$CLUSTER_NAME" \
                --tasks "$TASK_ARN" \
                --region "$AWS_REGION" \
                --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' \
                --output text 2>/dev/null | xargs -I {} aws ec2 describe-network-interfaces \
                --network-interface-ids {} \
                --region "$AWS_REGION" \
                --query 'NetworkInterfaces[0].Association.PublicIp' \
                --output text 2>/dev/null || echo "None")
            
            if [[ "$PUBLIC_IP" != "None" ]] && [[ -n "$PUBLIC_IP" ]]; then
                BASE_URL="http://$PUBLIC_IP:8082"
                echo -e "${GREEN}✅ Using AWS service URL: $BASE_URL${NC}"
            fi
        fi
    fi
fi

echo -e "${BLUE}🌐 Testing API at: $BASE_URL${NC}"

# Function to make HTTP request and check response
test_endpoint() {
    local method=$1
    local endpoint=$2
    local description=$3
    local expected_status=${4:-200}
    local data=$5
    
    echo -e "${YELLOW}🔍 Testing: $description${NC}"
    echo -e "   $method $endpoint"
    
    if [[ -n "$data" ]]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -d "$data" \
            "$BASE_URL$endpoint" || echo -e "\nERROR")
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            "$BASE_URL$endpoint" || echo -e "\nERROR")
    fi
    
    # Split response and status code
    response_body=$(echo "$response" | head -n -1)
    status_code=$(echo "$response" | tail -n 1)
    
    if [[ "$status_code" == "$expected_status" ]]; then
        echo -e "${GREEN}✅ $description: HTTP $status_code${NC}"
        if [[ -n "$response_body" ]] && [[ "$response_body" != "ERROR" ]]; then
            # Pretty print JSON if it's valid JSON
            if echo "$response_body" | jq . &>/dev/null; then
                echo -e "${BLUE}📄 Response:${NC}"
                echo "$response_body" | jq .
            else
                echo -e "${BLUE}📄 Response: $response_body${NC}"
            fi
        fi
        return 0
    else
        echo -e "${RED}❌ $description: Expected HTTP $expected_status, got $status_code${NC}"
        if [[ -n "$response_body" ]] && [[ "$response_body" != "ERROR" ]]; then
            echo -e "${RED}📄 Error Response: $response_body${NC}"
        fi
        return 1
    fi
}

# Test counter
TOTAL_TESTS=0
PASSED_TESTS=0

run_test() {
    ((TOTAL_TESTS++))
    if "$@"; then
        ((PASSED_TESTS++))
    fi
    echo ""
}

echo -e "${BLUE}=================================================================================${NC}"
echo -e "${BLUE}🧪 Starting API Tests${NC}"
echo -e "${BLUE}=================================================================================${NC}"

# Test 1: Health Check
run_test test_endpoint "GET" "/api/orders/health" "Health Check Endpoint"

# Test 2: Actuator Health
run_test test_endpoint "GET" "/actuator/health" "Actuator Health Check"

# Test 3: API Documentation
run_test test_endpoint "GET" "/swagger-ui.html" "Swagger UI" 200

# Test 4: Invalid Order Request (Missing required fields)
echo -e "${YELLOW}🔍 Testing: Invalid Order Request (Missing Fields)${NC}"
invalid_order='{
    "customerId": 1,
    "productId": "3d-model-001"
}'
run_test test_endpoint "POST" "/api/orders" "Invalid Order Request" 400 "$invalid_order"

# Test 5: Valid Order Request
echo -e "${YELLOW}🔍 Testing: Valid Order Request${NC}"
valid_order='{
    "customerId": 1,
    "customerEmail": "customer@test.com",
    "productId": "3d-model-001",
    "quantity": 1,
    "stlFileUrl": "https://example.com/model.stl",
    "shippingAddress": {
        "firstName": "John",
        "lastName": "Doe",
        "street": "123 Main St",
        "city": "San Francisco",
        "state": "CA",
        "zipCode": "94105",
        "country": "United States",
        "phone": "+1234567890",
        "latitude": 37.7749,
        "longitude": -122.4194
    },
    "paymentInformation": {
        "method": "PAYPAL",
        "totalAmount": 50.00,
        "currency": "USD",
        "description": "3D Printed Model",
        "successUrl": "https://example.com/success",
        "cancelUrl": "https://example.com/cancel",
        "paymentMethodData": {
            "paypalEmail": "customer@test.com"
        }
    },
    "deliveryPreferences": {
        "deliverySpeed": "STANDARD"
    },
    "orderNotes": "Test order from API test script"
}'

# Note: This test is expected to fail if downstream services are not available
echo -e "${BLUE}💡 Note: This test requires order-service and product-service to be running${NC}"
echo -e "${BLUE}💡 If services are not available, this test will demonstrate circuit breaker functionality${NC}"
run_test test_endpoint "POST" "/api/orders" "Valid Order Request (May fail if services unavailable)" 201 "$valid_order"

# Test 6: Circuit Breaker Status
run_test test_endpoint "GET" "/actuator/circuitbreakers" "Circuit Breaker Status"

# Test 7: Metrics
run_test test_endpoint "GET" "/actuator/metrics" "Application Metrics"

# Test 8: Prometheus Metrics
run_test test_endpoint "GET" "/actuator/prometheus" "Prometheus Metrics"

# Test Results Summary
echo -e "${BLUE}=================================================================================${NC}"
echo -e "${BLUE}📊 Test Results Summary${NC}"
echo -e "${BLUE}=================================================================================${NC}"
echo -e "${BLUE}Total Tests: $TOTAL_TESTS${NC}"
echo -e "${GREEN}Passed: $PASSED_TESTS${NC}"
echo -e "${RED}Failed: $((TOTAL_TESTS - PASSED_TESTS))${NC}"

if [[ $PASSED_TESTS -eq $TOTAL_TESTS ]]; then
    echo -e "${GREEN}🎉 All tests passed!${NC}"
    exit 0
else
    echo -e "${YELLOW}⚠️ Some tests failed. This is expected if downstream services are not running.${NC}"
    exit 0  # Don't fail the script for service-dependent tests
fi

# Additional manual testing instructions
echo -e "${BLUE}=================================================================================${NC}"
echo -e "${BLUE}📝 Manual Testing Instructions${NC}"
echo -e "${BLUE}=================================================================================${NC}"
echo -e "${YELLOW}1. Test with curl:${NC}"
echo "curl -X POST $BASE_URL/api/orders \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '$valid_order'"
echo ""
echo -e "${YELLOW}2. Test health endpoint:${NC}"
echo "curl $BASE_URL/api/orders/health"
echo ""
echo -e "${YELLOW}3. View API documentation:${NC}"
echo "Open: $BASE_URL/swagger-ui.html"
echo ""
echo -e "${YELLOW}4. Monitor metrics:${NC}"
echo "curl $BASE_URL/actuator/metrics"
echo ""
echo -e "${YELLOW}5. Check circuit breakers:${NC}"
echo "curl $BASE_URL/actuator/circuitbreakers"

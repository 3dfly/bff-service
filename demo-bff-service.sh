#!/bin/bash

# Demo Script for BFF Service
# This script demonstrates the complete order orchestration workflow

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

echo -e "${BLUE}🎯 BFF Service Demo - Complete Order Orchestration${NC}"
echo -e "${BLUE}================================================================${NC}"

# Configuration
BFF_URL="${BFF_URL:-http://localhost:8082}"
ORDER_SERVICE_URL="${ORDER_SERVICE_URL:-http://localhost:8080}"
PRODUCT_SERVICE_URL="${PRODUCT_SERVICE_URL:-http://localhost:8081}"

echo -e "${YELLOW}📋 Demo Configuration:${NC}"
echo -e "  BFF Service: $BFF_URL"
echo -e "  Order Service: $ORDER_SERVICE_URL"
echo -e "  Product Service: $PRODUCT_SERVICE_URL"
echo ""

# Function to check service health
check_service() {
    local service_name=$1
    local url=$2
    
    echo -e "${YELLOW}🔍 Checking $service_name...${NC}"
    
    if curl -f -s --max-time 5 "$url/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ $service_name is healthy${NC}"
        return 0
    else
        echo -e "${RED}❌ $service_name is not available${NC}"
        return 1
    fi
}

# Function to make HTTP request with JSON formatting
make_request() {
    local method=$1
    local url=$2
    local description=$3
    local data=$4
    
    echo -e "${PURPLE}📡 $description${NC}"
    echo -e "   $method $url"
    
    if [[ -n "$data" ]]; then
        echo -e "${BLUE}📤 Request:${NC}"
        echo "$data" | jq .
        
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -d "$data" \
            "$url" 2>/dev/null || echo -e "\nERROR")
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            "$url" 2>/dev/null || echo -e "\nERROR")
    fi
    
    # Split response and status code
    response_body=$(echo "$response" | head -n -1)
    status_code=$(echo "$response" | tail -n 1)
    
    echo -e "${BLUE}📥 Response (HTTP $status_code):${NC}"
    if [[ "$response_body" != "ERROR" ]] && [[ -n "$response_body" ]]; then
        if echo "$response_body" | jq . &>/dev/null; then
            echo "$response_body" | jq .
        else
            echo "$response_body"
        fi
    else
        echo "No response or error"
    fi
    
    echo ""
    return 0
}

echo -e "${BLUE}================================================================${NC}"
echo -e "${BLUE}🏥 Health Checks${NC}"
echo -e "${BLUE}================================================================${NC}"

# Check BFF Service health
check_service "BFF Service" "$BFF_URL"

# Try to check downstream services (may not be available)
check_service "Order Service" "$ORDER_SERVICE_URL" || echo -e "${YELLOW}⚠️ Order Service unavailable - circuit breaker will be tested${NC}"
check_service "Product Service" "$PRODUCT_SERVICE_URL" || echo -e "${YELLOW}⚠️ Product Service unavailable - circuit breaker will be tested${NC}"

echo ""

echo -e "${BLUE}================================================================${NC}"
echo -e "${BLUE}🎯 Order Orchestration Demo${NC}"
echo -e "${BLUE}================================================================${NC}"

# Demo order request
demo_order='{
  "customerId": 123,
  "customerEmail": "john.doe@example.com",
  "productId": "3d-miniature-dragon",
  "quantity": 2,
  "stlFileUrl": "https://cdn.example.com/models/dragon-miniature.stl",
  "shippingAddress": {
    "firstName": "John",
    "lastName": "Doe",
    "street": "123 Innovation Drive",
    "city": "San Francisco",
    "state": "CA",
    "zipCode": "94105",
    "country": "United States",
    "phone": "+1-555-0123",
    "latitude": 37.7749,
    "longitude": -122.4194
  },
  "paymentInformation": {
    "method": "PAYPAL",
    "totalAmount": 75.99,
    "currency": "USD",
    "description": "3D Printed Dragon Miniature (Qty: 2)",
    "successUrl": "https://app.threedfly.com/orders/success",
    "cancelUrl": "https://app.threedfly.com/orders/cancel",
    "paymentMethodData": {
      "paypalEmail": "john.doe@example.com"
    }
  },
  "deliveryPreferences": {
    "deliverySpeed": "EXPEDITED"
  },
  "orderNotes": "Please use high-quality resin for detailed miniatures"
}'

echo -e "${YELLOW}🎮 Testing Complete Order Flow:${NC}"
echo -e "  1. Find closest supplier (based on San Francisco location)"
echo -e "  2. Create order with supplier"
echo -e "  3. Process payment via PayPal"
echo -e "  4. Execute payment and revenue splitting"
echo ""

# Test the main order orchestration endpoint
make_request "POST" "$BFF_URL/api/orders" "🚀 Create and Process Complete Order" "$demo_order"

echo -e "${BLUE}================================================================${NC}"
echo -e "${BLUE}📊 Monitoring & Observability${NC}"
echo -e "${BLUE}================================================================${NC}"

# Test monitoring endpoints
make_request "GET" "$BFF_URL/api/orders/health" "🏥 BFF Service Health Check"
make_request "GET" "$BFF_URL/actuator/health" "🔧 Actuator Health Check"
make_request "GET" "$BFF_URL/actuator/metrics" "📈 Application Metrics"
make_request "GET" "$BFF_URL/actuator/circuitbreakers" "🔄 Circuit Breaker Status"

echo -e "${BLUE}================================================================${NC}"
echo -e "${BLUE}🧪 Error Handling Demo${NC}"
echo -e "${BLUE}================================================================${NC}"

# Test invalid requests
invalid_order='{
  "customerId": null,
  "customerEmail": "invalid-email",
  "productId": "",
  "quantity": 0
}'

make_request "POST" "$BFF_URL/api/orders" "❌ Invalid Order Request (Testing Validation)" "$invalid_order"

# Test with missing required fields
partial_order='{
  "customerId": 456,
  "productId": "test-product"
}'

make_request "POST" "$BFF_URL/api/orders" "❌ Incomplete Order Request (Missing Required Fields)" "$partial_order"

echo -e "${BLUE}================================================================${NC}"
echo -e "${BLUE}📚 API Documentation${NC}"
echo -e "${BLUE}================================================================${NC}"

echo -e "${YELLOW}📖 API Documentation Available:${NC}"
echo -e "  - Swagger UI: $BFF_URL/swagger-ui.html"
echo -e "  - API Docs: $BFF_URL/api-docs"
echo ""

echo -e "${YELLOW}🔍 Monitoring URLs:${NC}"
echo -e "  - Health: $BFF_URL/api/orders/health"
echo -e "  - Metrics: $BFF_URL/actuator/metrics"
echo -e "  - Circuit Breakers: $BFF_URL/actuator/circuitbreakers"
echo -e "  - Prometheus: $BFF_URL/actuator/prometheus"
echo ""

echo -e "${GREEN}🎉 BFF Service Demo Completed!${NC}"
echo ""
echo -e "${BLUE}📝 Key Features Demonstrated:${NC}"
echo -e "  ✅ Order Orchestration (4-step workflow)"
echo -e "  ✅ Service Integration (Order + Product services)"
echo -e "  ✅ Circuit Breaker Pattern"
echo -e "  ✅ Input Validation"
echo -e "  ✅ Error Handling"
echo -e "  ✅ Health Monitoring"
echo -e "  ✅ Metrics Collection"
echo ""
echo -e "${YELLOW}💡 Next Steps:${NC}"
echo -e "  1. Start Order Service (port 8080) for full functionality"
echo -e "  2. Start Product Service (port 8081) for payment processing"
echo -e "  3. Deploy to AWS using: ./scripts/setup-aws.sh && ./scripts/build-and-deploy.sh"
echo -e "  4. Monitor with: ./scripts/health-check.sh && ./scripts/view-logs.sh"

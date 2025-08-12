#!/bin/bash

# View Logs Script for BFF Service
# This script displays logs from the ECS service

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}📋 Viewing BFF Service logs${NC}"

# Load configuration
if [[ -f "aws-config.env" ]]; then
    source aws-config.env
    echo -e "${GREEN}✅ Configuration loaded${NC}"
else
    echo -e "${RED}❌ Configuration file 'aws-config.env' not found. Please run setup-aws.sh first.${NC}"
    exit 1
fi

# Parse command line arguments
FOLLOW_LOGS=false
LOG_LINES=100

while [[ $# -gt 0 ]]; do
    case $1 in
        -f|--follow)
            FOLLOW_LOGS=true
            shift
            ;;
        -n|--lines)
            LOG_LINES="$2"
            shift
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [-f|--follow] [-n|--lines NUMBER]"
            echo "  -f, --follow    Follow log output (tail -f)"
            echo "  -n, --lines     Number of lines to show (default: 100)"
            exit 0
            ;;
        *)
            echo -e "${RED}❌ Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Check if log group exists
if ! aws logs describe-log-groups --log-group-name-prefix "$LOG_GROUP_NAME" --region "$AWS_REGION" --query 'logGroups[0]' --output text | grep -q "$LOG_GROUP_NAME"; then
    echo -e "${RED}❌ Log group '$LOG_GROUP_NAME' not found${NC}"
    exit 1
fi

# Get log streams
echo -e "${YELLOW}🔍 Finding log streams...${NC}"
LOG_STREAMS=$(aws logs describe-log-streams \
    --log-group-name "$LOG_GROUP_NAME" \
    --order-by LastEventTime \
    --descending \
    --region "$AWS_REGION" \
    --query 'logStreams[].logStreamName' \
    --output text)

if [[ -z "$LOG_STREAMS" ]]; then
    echo -e "${RED}❌ No log streams found in group '$LOG_GROUP_NAME'${NC}"
    exit 1
fi

# Convert log streams to array
LOG_STREAM_ARRAY=($LOG_STREAMS)
LATEST_STREAM=${LOG_STREAM_ARRAY[0]}

echo -e "${GREEN}✅ Found ${#LOG_STREAM_ARRAY[@]} log stream(s)${NC}"
echo -e "${BLUE}📊 Using latest stream: $LATEST_STREAM${NC}"

if [[ "$FOLLOW_LOGS" == "true" ]]; then
    echo -e "${YELLOW}📺 Following logs (Press Ctrl+C to stop)...${NC}"
    echo -e "${BLUE}=================================================================================${NC}"
    
    # Follow logs using aws logs tail
    aws logs tail "$LOG_GROUP_NAME" \
        --follow \
        --region "$AWS_REGION" \
        --format short \
        --filter-pattern "" \
        --since "10 minutes ago"
else
    echo -e "${YELLOW}📺 Showing last $LOG_LINES lines...${NC}"
    echo -e "${BLUE}=================================================================================${NC}"
    
    # Get logs from the latest stream
    aws logs get-log-events \
        --log-group-name "$LOG_GROUP_NAME" \
        --log-stream-name "$LATEST_STREAM" \
        --region "$AWS_REGION" \
        --query "events[-$LOG_LINES:].message" \
        --output text
fi

echo -e "${BLUE}=================================================================================${NC}"
echo -e "${GREEN}✅ Log viewing completed${NC}"

# Show additional information
echo -e "${BLUE}💡 Additional commands:${NC}"
echo -e "  - Follow all logs: ${YELLOW}$0 --follow${NC}"
echo -e "  - Show more lines: ${YELLOW}$0 --lines 500${NC}"
echo -e "  - View in AWS Console: ${YELLOW}https://console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#logsV2:log-groups/log-group/$(echo "$LOG_GROUP_NAME" | sed 's/\//%252F/g')${NC}"

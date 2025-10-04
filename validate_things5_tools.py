#!/usr/bin/env python3
"""
Script to validate Things5 MCP tools schemas
Identifies tools with invalid schemas (e.g., arrays without items)
"""

import requests
import json
import sys

# Things5 credentials
KEYCLOAK_URL = "https://auth.things5.digital/auth/realms/demo10/protocol/openid-connect/token"
MCP_URL = "https://things5-mcp-server.onrender.com/mcp"
USERNAME = "ddellecasedata+test@gmail.com"
PASSWORD = "Password"

def get_oauth_token():
    """Get OAuth token from Keycloak"""
    response = requests.post(
        KEYCLOAK_URL,
        data={
            'client_id': 'api',
            'grant_type': 'password',
            'scope': 'openid',
            'username': USERNAME,
            'password': PASSWORD
        }
    )
    response.raise_for_status()
    return response.json()['access_token']

def initialize_session(token):
    """Initialize MCP session"""
    response = requests.post(
        MCP_URL,
        headers={
            'Content-Type': 'application/json',
            'Accept': 'application/json, text/event-stream',
            'Authorization': f'Bearer {token}'
        },
        json={
            'jsonrpc': '2.0',
            'id': 1,
            'method': 'initialize',
            'params': {
                'protocolVersion': '2024-11-05',
                'capabilities': {},
                'clientInfo': {'name': 'Tool Validator', 'version': '1.0'}
            }
        }
    )
    response.raise_for_status()
    return response.json()

def get_tools(token):
    """Get all tools from MCP server"""
    response = requests.post(
        MCP_URL,
        headers={
            'Content-Type': 'application/json',
            'Accept': 'application/json, text/event-stream',
            'Authorization': f'Bearer {token}'
        },
        json={
            'jsonrpc': '2.0',
            'id': 2,
            'method': 'tools/list'
        }
    )
    
    if response.status_code != 200:
        print(f"Error response: {response.text}")
        response.raise_for_status()
    
    return response.json().get('result', {}).get('tools', [])

def validate_tool_schema(tool):
    """Validate a single tool schema"""
    name = tool.get('name', 'unknown')
    params = tool.get('inputSchema', {})
    properties = params.get('properties', {})
    
    invalid_props = []
    
    for prop_name, prop_schema in properties.items():
        # Arrays must have 'items' defined
        if prop_schema.get('type') == 'array' and 'items' not in prop_schema:
            invalid_props.append(prop_name)
    
    return invalid_props

def main():
    print("🔐 Getting OAuth token...")
    try:
        token = get_oauth_token()
        print("✅ Token obtained\n")
    except Exception as e:
        print(f"❌ Failed to get token: {e}")
        sys.exit(1)
    
    print("🔌 Initializing MCP session...")
    try:
        init_result = initialize_session(token)
        print(f"✅ Session initialized\n")
    except Exception as e:
        print(f"❌ Failed to initialize: {e}")
        sys.exit(1)
    
    print("📡 Getting tools from MCP server...")
    try:
        tools = get_tools(token)
        print(f"✅ Got {len(tools)} tools\n")
    except Exception as e:
        print(f"❌ Failed to get tools: {e}")
        sys.exit(1)
    
    print("=" * 60)
    print("VALIDATING TOOL SCHEMAS")
    print("=" * 60)
    
    valid_tools = []
    invalid_tools = []
    
    for tool in tools:
        name = tool.get('name', 'unknown')
        invalid_props = validate_tool_schema(tool)
        
        if invalid_props:
            invalid_tools.append({
                'name': name,
                'invalid_properties': invalid_props
            })
            print(f"❌ {name}")
            for prop in invalid_props:
                print(f"   → Property '{prop}' is array without 'items'")
        else:
            valid_tools.append(name)
            print(f"✅ {name}")
    
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"Total tools: {len(tools)}")
    print(f"✅ Valid tools: {len(valid_tools)}")
    print(f"❌ Invalid tools: {len(invalid_tools)}")
    
    if invalid_tools:
        print("\n" + "=" * 60)
        print("TOOLS TO FIX ON SERVER")
        print("=" * 60)
        for tool in invalid_tools:
            print(f"\n📝 {tool['name']}:")
            print("   Fix required:")
            for prop in tool['invalid_properties']:
                print(f"   - Property '{prop}' needs 'items' definition")
                print(f"     Example: \"items\": {{\"type\": \"string\"}}")
    
    print("\n" + "=" * 60)
    
    return 0 if not invalid_tools else 1

if __name__ == "__main__":
    sys.exit(main())

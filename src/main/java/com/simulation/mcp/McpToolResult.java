package com.simulation.mcp;

import java.util.Map;

/**
 * Standard Model Context Protocol (MCP) tool execution result carrier.
 *
 * @param isError True if tool execution resulted in an error
 * @param content Structured payload returned by the tool
 * @param message Diagnostic or human-readable status message
 */
public record McpToolResult(
        boolean isError,
        Object content,
        String message
) {
    public static McpToolResult success(Object content) {
        return new McpToolResult(false, content, "Success");
    }

    public static McpToolResult success(Object content, String message) {
        return new McpToolResult(false, content, message);
    }

    public static McpToolResult error(String message) {
        return new McpToolResult(true, Map.of("error", message), message);
    }

    public boolean isSuccess() {
        return !isError;
    }
}

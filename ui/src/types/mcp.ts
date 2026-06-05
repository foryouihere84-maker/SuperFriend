export interface MCPServer {
  name: string
  description: string
  command: string
  args: string[]
  env?: Record<string, string>
  selected: boolean
  running: boolean
  toolCount: number
}

export interface MCPServerConfig {
  mcpServers: Record<string, MCPServerConfigItem>
}

export interface MCPServerConfigItem {
  command: string
  args: string[]
  env?: Record<string, string>
}

export interface MCPServerStats {
  totalServers: number
  selectedServers: number
  runningServers: number
  totalTools: number
}

export interface MCPServersResponse {
  servers: MCPServer[]
  stats: MCPServerStats
}

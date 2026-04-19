import { defineConfig } from 'vitest/config';
// 如果上面的导入报错，可以尝试安装 vitest: npm install -D vitest
// 或者检查 tsconfig.json 中的 moduleResolution 设置是否为 "bundler" 或 "node"

export default defineConfig({
    test: {
        globals: true,
        environment: 'node',
        include: ['tests/**/*.test.ts'],
        coverage: {
            provider: 'v8',
            reporter: ['text', 'json', 'html'],
            include: ['src/**/*.ts'],
            exclude: ['src/index.ts']
        }
    }
});

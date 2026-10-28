import {defineConfig} from 'vitest/config'
import {readFileSync} from 'node:fs'
const shared=JSON.parse(readFileSync(new URL('./apps/shared/package.json',import.meta.url),'utf8'))
const aliases=Object.entries(shared.exports).map(([key,value])=>({find:key==='.'?/^@hermes\/shared$/:`@hermes/shared/${key.slice(2)}`,replacement:new URL(`./apps/shared/${value}`,import.meta.url).pathname}))
export default defineConfig({test:{environment:'jsdom',include:['apps/desktop/src/app/chat/composer/paste-url*.test.tsx','apps/desktop/src/app/chat/composer/rich-editor.test.ts','apps/desktop/src/app/chat/composer/empty-composer.test.ts','apps/desktop/src/app/chat/composer/url-refs.test.ts','apps/desktop/src/app/chat/composer/undo-cut-drag.test.tsx'],testTimeout:15000},resolve:{alias:[{find:'@',replacement:new URL('./apps/desktop/src',import.meta.url).pathname},...aliases,{find:'@hermes/shared',replacement:new URL('./apps/shared/src',import.meta.url).pathname}]}})

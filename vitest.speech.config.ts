import {defineConfig} from 'vitest/config'
import {readFileSync} from 'node:fs'
const shared = JSON.parse(readFileSync(new URL('./apps/shared/package.json', import.meta.url), 'utf8'))
const aliases = Object.entries(shared.exports).map(([key, value]) => ({find: key==='.' ? /^@hermes\/shared$/ : `@hermes/shared/${key.slice(2)}`, replacement: new URL(`./apps/shared/${value}`, import.meta.url).pathname}))
export default defineConfig({test:{environment:'jsdom',include:['apps/desktop/src/store/voice-prefs.test.ts','apps/desktop/src/lib/voice-playback.routing.test.ts','apps/desktop/src/lib/voice-playback.same-turn.test.ts','apps/desktop/src/app/chat/composer/hooks/use-auto-speak-replies.owner.test.tsx'],testTimeout:15000},resolve:{alias:[{find:'@',replacement:new URL('./apps/desktop/src',import.meta.url).pathname},...aliases]}})

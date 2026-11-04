import { Medal, Save } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { getGamificationSettings, saveRankTiers, saveXpRule, type RankTier, type XpRule } from '../api'
import { RankInsignia } from '../feed/RankInsignia'
import './rank.css'

/** Administrador: XP por acao (valor, limite diario, desconto entre as mesmas contas) e faixas das patentes. */
export function AdminGamificationPanel() {
  const [rules, setRules] = useState<XpRule[]>([])
  const [tiers, setTiers] = useState<RankTier[]>([])
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState('')

  const load = useCallback(async () => {
    try { const data = await getGamificationSettings(); setRules(data.rules); setTiers(data.tiers) }
    catch (e) { setMessage(e instanceof Error ? e.message : 'Não foi possível carregar.') }
  }, [])
  useEffect(() => { void load() }, [load])

  const patch = (code: string, change: Partial<XpRule>) => setRules(list => list.map(rule => rule.actionCode === code ? { ...rule, ...change } : rule))
  const save = async (key: string, action: () => Promise<{ message: string }>) => {
    setBusy(key); setMessage('')
    try { setMessage((await action()).message); await load() }
    catch (e) { setMessage(e instanceof Error ? e.message : 'Não foi possível salvar.') }
    finally { setBusy('') }
  }

  if (!rules.length && !message) return null
  return <details className="rank-admin">
    <summary><Medal/> Patentes e XP (administração)</summary>
    {message && <p className="rank-admin-message" role="status">{message}</p>}
    <h3>XP por ação</h3>
    <p className="rank-admin-note">Mudanças valem para as próximas ações. "Mesmo par" reduz o XP quando as mesmas duas contas interagem muito (1 = sem desconto).</p>
    <ul className="rank-admin-rules">{rules.map(rule => <li key={rule.actionCode}>
      <span><b>{rule.description}</b><small>{rule.actionCode}</small></span>
      <label><small>XP</small><input type="number" min={0} max={1000} value={rule.xp} onChange={e => patch(rule.actionCode, { xp: Number(e.target.value) })}/></label>
      <label><small>Limite/dia</small><input type="number" min={1} max={1000} value={rule.dailyCap ?? ''} placeholder="—"
        onChange={e => patch(rule.actionCode, { dailyCap: e.target.value === '' ? null : Number(e.target.value) })}/></label>
      <label><small>Mesmo par</small><input type="number" min={0} max={1} step={0.05} value={rule.samePairFactor} onChange={e => patch(rule.actionCode, { samePairFactor: Number(e.target.value) })}/></label>
      <label className="rank-admin-check"><input type="checkbox" checked={rule.active} onChange={e => patch(rule.actionCode, { active: e.target.checked })}/> Ativa</label>
      <button type="button" className="module-secondary" disabled={busy === rule.actionCode} onClick={() => void save(rule.actionCode, () => saveXpRule(rule))}><Save/> Salvar</button>
    </li>)}</ul>
    <h3>Faixas das patentes</h3>
    <p className="rank-admin-note">XP mínimo de cada patente, sempre crescente. As patentes de todos são recalculadas, sem aviso de promoção.</p>
    <ul className="rank-admin-tiers">{tiers.map(tier => <li key={tier.code}>
      <RankInsignia code={tier.code} size={22}/><span>{tier.name}</span>
      <input type="number" min={0} value={tier.minXp} disabled={tier.position === 1} aria-label={`XP mínimo de ${tier.name}`}
        onChange={e => setTiers(list => list.map(item => item.code === tier.code ? { ...item, minXp: Number(e.target.value) } : item))}/>
    </li>)}</ul>
    <button type="button" className="module-primary" disabled={busy === 'tiers'}
      onClick={() => void save('tiers', () => saveRankTiers(Object.fromEntries(tiers.map(tier => [tier.code, tier.minXp]))))}><Save/> Salvar faixas</button>
  </details>
}

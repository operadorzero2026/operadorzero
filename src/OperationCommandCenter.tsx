import { FormEvent, useCallback, useEffect, useState } from 'react'
import { MessageCircle, Plus, Radio, Shield, Users } from 'lucide-react'
import { createOperationSquad, createOperationTeam, getOperationChat, Operation, OperationChatPage, OperationStructure, sendOperationChat } from './api'

const sizeLabel={SMALL:'Pequeno',MEDIUM:'Médio',LARGE:'Grande'}
export function OperationCommandCenter({operation,initialStructure}:{operation:Operation;initialStructure:OperationStructure|null}){
  const [structure,setStructure]=useState<OperationStructure|null>(initialStructure),[chat,setChat]=useState<OperationChatPage|null>(null),[channel,setChannel]=useState<string>('general'),[chatOpen,setChatOpen]=useState(false),[error,setError]=useState(''),[busy,setBusy]=useState(false)
  const loadChat=useCallback(async()=>{try{setChat(await getOperationChat(operation.id,channel==='general'?null:channel))}catch(e){setChat(null);setError(e instanceof Error?e.message:'Chat indisponível.')}},[operation.id,channel])
  useEffect(()=>setStructure(initialStructure),[initialStructure]);useEffect(()=>{if(!chatOpen)return;void loadChat();const timer=window.setInterval(()=>void loadChat(),15000);return()=>window.clearInterval(timer)},[chatOpen,loadChat])
  const addTeam=async(e:FormEvent<HTMLFormElement>)=>{e.preventDefault();const d=new FormData(e.currentTarget);setBusy(true);try{setStructure(await createOperationTeam(operation.id,{name:d.get('name'),acronym:d.get('acronym'),color:d.get('color'),capacity:Number(d.get('capacity')),sortOrder:null,entriesOpen:true}));e.currentTarget.reset()}catch(x){setError(x instanceof Error?x.message:'Não foi possível criar o time.')}finally{setBusy(false)}}
  const addSquad=async(teamId:string)=>{const name=window.prompt('Nome do novo esquadrão');if(!name)return;const capacity=Number(window.prompt('Capacidade do esquadrão','10'));if(!capacity)return;try{setStructure(await createOperationSquad(operation.id,teamId,{name,acronym:null,description:null,capacity,sortOrder:null,entriesOpen:true}))}catch(x){setError(x instanceof Error?x.message:'Não foi possível criar o esquadrão.')}}
  const send=async(e:FormEvent<HTMLFormElement>)=>{e.preventDefault();const form=e.currentTarget,d=new FormData(form),body=String(d.get('message')||'');if(!body.trim())return;setBusy(true);try{await sendOperationChat(operation.id,body,channel==='general'?null:channel);form.reset();await loadChat()}catch(x){setError(x instanceof Error?x.message:'Não foi possível enviar.')}finally{setBusy(false)}}
  if(!structure)return <p>Carregando organização da operação…</p>
  const limit=structure.participantLimit
  return <section className="operation-command-center">
    <header><div><small>CENTRAL DA OPERAÇÃO</small><h3>{sizeLabel[structure.gameSize]}</h3></div><strong>{structure.participantCount}/{limit??'sem limite'}</strong></header>
    <div className="operation-occupancy"><i style={{width:`${limit?Math.min(100,structure.participantCount/limit*100):0}%`}} /></div>
    {error&&<p className="module-feedback" role="alert">{error}</p>}
    <button type="button" className="module-secondary operation-chat-toggle" onClick={()=>setChatOpen(open=>!open)}><MessageCircle/> {chatOpen?'Fechar comunicação':'Abrir comunicação'}</button>
    <div className={chatOpen?'operation-command-layout':'operation-command-layout structure-only'}><div>
      <h4><Users/> Times e esquadrões</h4>
      <div className="operation-structure-grid">{structure.teams.map(team=><article key={team.id} style={{borderColor:team.color}}>
        <header><span className="team-color" style={{background:team.color}}/><div><b>{team.name}</b><small>{team.participantCount}/{team.capacity} · {team.status==='OPEN'?'aberto':'fechado'}</small></div></header>
        {(team.commanderCallsign||team.radioCallsign)&&<p><Shield/> {team.commanderCallsign||'Sem comandante'} · <Radio/> {team.radioCallsign||'Sem rádio'}</p>}
        {team.squads.map(s=><div className="operation-squad" key={s.id}><b>{s.name}</b><span>{s.participantCount}/{s.capacity}</span></div>)}
        {structure.managedByCurrentUser&&structure.gameSize!=='SMALL'&&<button onClick={()=>void addSquad(team.id)}><Plus/> Esquadrão</button>}
      </article>)}</div>
      {structure.managedByCurrentUser&&<form className="operation-team-create" onSubmit={addTeam}><input name="name" placeholder="Nome do time" maxLength={80} required/><input name="acronym" placeholder="Sigla" maxLength={12}/><input name="color" type="color" defaultValue="#6f7839"/><input name="capacity" type="number" min={1} placeholder="Vagas" required/><button disabled={busy}><Plus/> Criar time</button></form>}
    </div>{chatOpen&&<div className="operation-chat-panel"><h4><MessageCircle/> Comunicação</h4><select value={channel} onChange={e=>setChannel(e.target.value)}><option value="general">Chat geral</option>{structure.teams.map(t=><option key={t.id} value={t.id}>Chat · {t.name}</option>)}</select>
      <div className="operation-chat-messages">{chat?.items.slice().reverse().map(m=><p key={m.id} className={m.official?'official':''}><b>{m.author.callsign}</b><span>{m.body||'Mensagem removida'}</span><small>{new Date(m.createdAt).toLocaleString('pt-BR')}</small></p>)}{chat?.items.length===0&&<small>Nenhuma mensagem neste canal.</small>}</div>
      <form onSubmit={send}><textarea name="message" maxLength={2000} placeholder="Escreva uma mensagem" required/><button className="module-primary" disabled={busy||chat?.locked}>Enviar</button></form>
    </div>}</div>
  </section>
}

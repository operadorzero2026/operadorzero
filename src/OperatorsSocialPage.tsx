import { useCallback, useEffect, useMemo, useState } from 'react'
import { ArrowLeft, Bookmark, CalendarDays, Check, ChevronDown, Flag, MapPin, Medal, MessageCircle, MoreHorizontal, Radio, Search, Shield, ShieldBan, Target, ThumbsUp, UserMinus, UserPlus, Users } from 'lucide-react'
import {
  acceptOperatorFriendship, blockOperator, cancelOperatorFriendship, communityImageUrl, CommunityPostSummary,
  declineOperatorFriendship, getBlockedOperators, getCommunityPosts, getOperatorConnectionStatus, getOperatorConnections,
  getOperatorProfileOverview, OperatorConnection, OperatorConnectionStatus, OperatorProfileOverview, OperatorSummary,
  operatorPhotoUrl, operationCoverUrl, removeOperatorFriendship, reportOperator, requestOperatorFriendship,
  searchOperators, toggleCommunityBookmark, toggleCommunityVote, unblockOperator,
} from './api'
import { operatorTeamLabel } from './operator-label'

type Props = { path:string;currentUsername:string;onNavigate:(path:string)=>void;onEditProfile:()=>void }
const specialPaths=['/operadores/amigos','/operadores/solicitacoes','/operadores/buscar']
const profileUsername=(path:string,current:string)=>path==='/operadores'?current:!specialPaths.includes(path)&&path.startsWith('/operadores/')?decodeURIComponent(path.slice(12)):''
const initials=(operator:Pick<OperatorSummary,'callsign'|'displayName'>)=>(operator.callsign||operator.displayName||'?').slice(0,1).toUpperCase()
const location=(operator:OperatorSummary)=>operator.city?`${operator.city}${operator.stateCode?` - ${operator.stateCode}`:''}`:'Localização não informada'
const teamRole=(value?:string|null)=>({CAPTAIN:'Capitão',MANAGER:'Administrador',MEMBER:'Integrante'}[value||'']||'Integrante')
const position=(value?:string|null)=>({ASSAULT:'Assalto',SUPPORT:'Suporte',SNIPER:'Sniper',DMR:'DMR',MEDIC:'Médico',COMMANDER:'Comandante',RADIO_OPERATOR:'Operador de rádio'}[value||'']||value||'Não informada')

function Avatar({operator,image}: {operator:Pick<OperatorSummary,'callsign'|'displayName'>;image?:string}) {
  return <span className="oz-social-avatar">{image?<img src={image} alt=""/>:initials(operator)}</span>
}

export function OperatorsSocialPage({path,currentUsername,onNavigate,onEditProfile}:Props) {
  if(path==='/operadores/buscar') return <main className="oz-social oz-social-directory"><DirectoryNav path={path} onNavigate={onNavigate}/><OperatorFinder onNavigate={onNavigate}/></main>
  if(path==='/operadores/amigos') return <main className="oz-social oz-social-directory"><DirectoryNav path={path} onNavigate={onNavigate}/><Connections mode="ACCEPTED" onNavigate={onNavigate}/></main>
  if(path==='/operadores/solicitacoes') return <main className="oz-social oz-social-directory"><DirectoryNav path={path} onNavigate={onNavigate}/><Connections mode="PENDING" onNavigate={onNavigate}/></main>
  return <PublicProfile username={profileUsername(path,currentUsername)||currentUsername} own={(profileUsername(path,currentUsername)||currentUsername)===currentUsername} onNavigate={onNavigate} onEditProfile={onEditProfile}/>
}

function DirectoryNav({path,onNavigate}:{path:string;onNavigate:(path:string)=>void}) { return <header className="oz-social-heading"><div><small>REDE DE OPERADORES</small><h1>Operadores</h1></div><nav><button onClick={()=>onNavigate('/operadores')}>Meu perfil</button><button className={path.endsWith('buscar')?'active':''} onClick={()=>onNavigate('/operadores/buscar')}>Buscar operadores</button><button className={path.endsWith('amigos')?'active':''} onClick={()=>onNavigate('/operadores/amigos')}>Amigos</button><button className={path.endsWith('solicitacoes')?'active':''} onClick={()=>onNavigate('/operadores/solicitacoes')}>Solicitações</button></nav></header> }

function PublicProfile({username,own,onNavigate,onEditProfile}:{username:string;own:boolean;onNavigate:(path:string)=>void;onEditProfile:()=>void}) {
  const [profile,setProfile]=useState<OperatorProfileOverview|null>(null)
  const [connection,setConnection]=useState<OperatorConnectionStatus|null>(null)
  const [posts,setPosts]=useState<CommunityPostSummary[]>([])
  const [tab,setTab]=useState<'feed'|'about'|'operations'|'teams'|'friends'>('feed')
  const [notice,setNotice]=useState(''),[pending,setPending]=useState(false),[menu,setMenu]=useState(false)
  const load=useCallback(async()=>{setNotice('');try{const [data,postPage]=await Promise.all([getOperatorProfileOverview(username),getCommunityPosts({sort:'RECENT',size:50})]);setProfile(data);setPosts(postPage.items.filter(post=>post.author.username.toLowerCase()===username.toLowerCase()));if(!own)setConnection(await getOperatorConnectionStatus(username))}catch(error){setNotice(error instanceof Error?error.message:'Não foi possível carregar o perfil.')}},[username,own])
  useEffect(()=>{void load()},[load])
  const experience=useMemo(()=>{if(!profile?.operator.airsoftStartedAt)return null;const year=new Date(profile.operator.airsoftStartedAt+'T12:00:00').getFullYear();return {year,years:Math.max(0,new Date().getFullYear()-year)}},[profile])
  const connect=async()=>{if(!connection)return;setPending(true);try{if(connection.status==='NONE')setConnection(await requestOperatorFriendship(username));else if(connection.status==='PENDING_SENT'&&connection.connectionId){await cancelOperatorFriendship(connection.connectionId);setConnection({status:'NONE'})}else if(connection.status==='ACCEPTED'&&connection.connectionId){await removeOperatorFriendship(connection.connectionId);setConnection({status:'NONE'})}}catch(e){setNotice(e instanceof Error?e.message:'Não foi possível concluir.')}finally{setPending(false)}}
  if(!profile)return <main className="oz-profile-page"><div className="oz-profile-loading">{notice||'Carregando perfil do operador…'}</div></main>
  const op=profile.operator
  const photo=profile.hasPhoto?operatorPhotoUrl(profile.photoVersion):undefined
  const metrics=[['PUBLICAÇÕES',profile.publicationCount],['AMIGOS',profile.friendCount],['OPERAÇÕES',profile.operationCount],['RANKING',profile.rankingPosition?`#${profile.rankingPosition}`:'—'],['CONQUISTAS',profile.achievementCount]]
  return <main className="oz-profile-page">
    <header className="oz-profile-mobile-header"><button onClick={()=>history.back()} aria-label="Voltar"><ArrowLeft/></button><b>OPERADOR <em>ZERO</em></b><span><button aria-label="Mais opções" onClick={()=>setMenu(!menu)}><MoreHorizontal/></button></span></header>
    <section className="oz-profile-hero">
      <img className="oz-profile-cover-image" src="/operador-zero-identity.jpeg" alt=""/>
      <div className="oz-profile-cover-shade"/>
      <div className="oz-profile-identity">
        <div className="oz-profile-avatar-wrap"><Avatar operator={op} image={photo}/><i title="Online"/></div>
        <div className="oz-profile-name"><h1>{op.callsign||op.displayName}<span title="Perfil ativo">◆</span></h1><p>{op.displayName}</p><small><MapPin/>{location(op)}</small></div>
        <div className="oz-profile-actions">
          {own?<button className="primary" onClick={onEditProfile}><UserPlus/> Editar perfil</button>:<button className="primary" disabled={pending||connection?.status==='BLOCKED'||connection?.status==='PENDING_RECEIVED'} onClick={connect}>{connection?.status==='ACCEPTED'?<><Check/> Amigos <ChevronDown/></>:connection?.status==='PENDING_SENT'?'Solicitação enviada':connection?.status==='PENDING_RECEIVED'?'Responder solicitação':<><UserPlus/> Adicionar amigo</>}</button>}
          <button disabled title="Mensagens privadas serão disponibilizadas futuramente"><MessageCircle/> Mensagem</button>
          <div className="oz-profile-more"><button aria-label="Mais opções" onClick={()=>setMenu(!menu)}><MoreHorizontal/></button>{menu&&<div>{!own&&<><button onClick={async()=>{await blockOperator(username);setConnection({status:'BLOCKED'});setMenu(false)}}><ShieldBan/> Bloquear</button><button onClick={async()=>{await reportOperator(username,'OTHER','Perfil encaminhado para análise.');setNotice('Denúncia enviada para a moderação.');setMenu(false)}}><Flag/> Denunciar</button></>}<button onClick={()=>navigator.clipboard?.writeText(window.location.href)}>Copiar link</button></div>}</div>
        </div>
      </div>
      <div className="oz-profile-professional">
        <div><small>Joga airsoft desde</small><b>{experience?.year||'Não informado'}</b><span>{experience?`${experience.years} ${experience.years===1?'ano':'anos'} de experiência`:'Adicione esta informação ao perfil'}</span></div>
        <div><small>Equipe atual</small><b>{op.teamName||'SEM TIME'}</b><span>{op.teamName?teamRole(profile.teamRole):'Nenhuma equipe vinculada'}</span></div>
        <div><small>Especialidade</small><b>{position(profile.preferredPosition)}</b><span>Posição principal</span></div>
        <div><small>Nível</small><b>—</b><span>Nível ainda não calculado</span><i className="oz-xp"><i/></i></div>
      </div>
    </section>
    {notice&&<p className="oz-social-notice" role="status">{notice}</p>}
    <nav className="oz-profile-metrics" aria-label="Estatísticas do perfil">{metrics.map(([label,value])=><button key={label} onClick={()=>label==='AMIGOS'?onNavigate('/operadores/amigos'):label==='OPERAÇÕES'?setTab('operations'):undefined}><b>{value}</b><small>{label}</small></button>)}</nav>
    <nav className="oz-profile-tabs">{([['feed','Feed'],['about','Sobre'],['operations','Operações'],['teams','Equipes'],['friends','Amigos']] as const).map(([id,label])=><button key={id} className={tab===id?'active':''} onClick={()=>setTab(id)}>{id==='feed'?<Radio/>:id==='operations'?<Target/>:id==='teams'?<Shield/>:<Users/>}{label}</button>)}</nav>
    <section className="oz-profile-body">
      <aside className="oz-profile-left">
        <ProfileCard title="SOBRE"><p>{profile.bio||'Nenhuma apresentação adicionada.'}</p>{profile.preferredPosition&&<mark>{position(profile.preferredPosition)}</mark>}</ProfileCard>
        <ProfileCard title={`AMIGOS EM COMUM (${profile.mutualFriends.length})`}>{profile.mutualFriends.length?<div className="oz-mutuals">{profile.mutualFriends.map(friend=><button key={friend.id} onClick={()=>onNavigate(`/operadores/${friend.username}`)} title={friend.callsign}><Avatar operator={friend}/></button>)}</div>:<p>Nenhum amigo em comum.</p>}</ProfileCard>
        <ProfileCard title="EQUIPAMENTOS PRINCIPAIS">{profile.equipment.length?<ul className="oz-equipment">{profile.equipment.map((item,index)=><li key={`${item.category}-${index}`}><Target/><span><small>{item.category}</small>{item.name}</span></li>)}</ul>:<p>Nenhum equipamento público.</p>}</ProfileCard>
      </aside>
      <section className="oz-profile-feed">
        {tab==='feed'&&<ProfileFeed posts={posts} own={own} onNavigate={onNavigate} onChange={setPosts}/>} 
        {tab==='about'&&<ProfileCard title="SOBRE O OPERADOR"><p>{profile.bio||'Nenhuma apresentação adicionada.'}</p></ProfileCard>}
        {tab==='operations'&&<OperationsList profile={profile} onNavigate={onNavigate}/>} 
        {tab==='teams'&&<ProfileCard title="EQUIPE ATUAL"><h3>{op.teamName||'SEM TIME'}</h3><p>{op.teamName?`${op.teamAcronym||op.teamName} · ${teamRole(profile.teamRole)}`:'Este operador não integra uma equipe.'}</p></ProfileCard>}
        {tab==='friends'&&<ProfileCard title="AMIZADES"><p>{profile.friendCount?`${profile.friendCount} amizade${profile.friendCount===1?'':'s'} registrada${profile.friendCount===1?'':'s'}.`:'Nenhuma amizade registrada.'}</p></ProfileCard>}
      </section>
      <aside className="oz-profile-right"><OperationsList profile={profile} onNavigate={onNavigate}/><ProfileCard title="CONQUISTAS"><div className="oz-achievement"><Medal/><div><b>Nenhuma conquista registrada</b><p>As conquistas aparecerão após serem concedidas pelo sistema.</p></div></div></ProfileCard></aside>
    </section>
    {own&&<button className="oz-new-post" onClick={()=>onNavigate('/comunidade/nova')}><b>+</b> Nova publicação</button>}
  </main>
}

function ProfileCard({title,children}:{title:string;children:React.ReactNode}) { return <section className="oz-profile-card"><h2>{title}</h2>{children}</section> }
function OperationsList({profile,onNavigate}:{profile:OperatorProfileOverview;onNavigate:(path:string)=>void}) { return <ProfileCard title="ÚLTIMAS OPERAÇÕES">{profile.recentOperations.length?<div className="oz-recent-operations">{profile.recentOperations.map(item=><button key={item.id} onClick={()=>onNavigate(`/operacoes/${item.id}`)}>{item.hasCover?<img src={operationCoverUrl(item.id,item.coverVersion)} alt=""/>:<CalendarDays/>}<span><b>{item.name}</b><small>{new Date(item.date+'T12:00:00').toLocaleDateString('pt-BR')} · {item.city}/{item.stateCode}</small></span></button>)}</div>:<p>Nenhuma operação concluída.</p>}</ProfileCard> }

function ProfileFeed({posts,own,onNavigate,onChange}:{posts:CommunityPostSummary[];own:boolean;onNavigate:(path:string)=>void;onChange:(items:CommunityPostSummary[])=>void}) {
  const toggle=async(post:CommunityPostSummary,kind:'vote'|'save')=>{const response=kind==='vote'?await toggleCommunityVote(post.id):await toggleCommunityBookmark(post.id);onChange(posts.map(item=>item.id===post.id?{...item,...(kind==='vote'?{votedByCurrentUser:response.active,voteCount:response.count}:{savedByCurrentUser:response.active})}:item))}
  if(!posts.length)return <div className="oz-profile-empty"><MessageCircle/><h2>Nenhuma publicação ainda.</h2>{own&&<button onClick={()=>onNavigate('/comunidade/nova')}>Criar primeira publicação</button>}</div>
  return <div className="oz-profile-posts">{posts.map(post=><article key={post.id}><header><Avatar operator={post.author}/><span><b>{operatorTeamLabel(post.author)}</b><small>{location(post.author)} · {new Date(post.createdAt).toLocaleDateString('pt-BR')}</small></span><button><MoreHorizontal/></button></header><button className="oz-post-copy" onClick={()=>onNavigate(`/comunidade/publicacoes/${post.id}`)}><h2>{post.title}</h2><p>{post.description}</p></button>{post.coverImageId&&<button className="oz-post-media" onClick={()=>onNavigate(`/comunidade/publicacoes/${post.id}`)}><img src={communityImageUrl(post.coverImageId)} alt={`Imagem da publicação ${post.title}`}/></button>}<footer><button className={post.votedByCurrentUser?'active':''} onClick={()=>toggle(post,'vote')}><ThumbsUp/>{post.voteCount}</button><button onClick={()=>onNavigate(`/comunidade/publicacoes/${post.id}`)}><MessageCircle/>{post.commentCount}</button><button><span>↗</span> Compartilhar</button><button className={post.savedByCurrentUser?'active':''} onClick={()=>toggle(post,'save')}><Bookmark/></button></footer></article>)}</div>
}

function OperatorFinder({onNavigate}:{onNavigate:(path:string)=>void}) { const [query,setQuery]=useState(''),[items,setItems]=useState<OperatorSummary[]>([]);useEffect(()=>{if(query.trim().length<2){setItems([]);return}const timer=setTimeout(()=>void searchOperators(query).then(r=>setItems(r.items)),300);return()=>clearTimeout(timer)},[query]);return <section className="oz-social-panel"><label className="oz-social-search"><Search/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar por nick ou nome"/></label><div className="oz-social-people">{items.map(item=><button key={item.id} onClick={()=>onNavigate(`/operadores/${item.username}`)}><Avatar operator={item}/><span><b>{operatorTeamLabel(item)}</b><small>@{item.username} · {location(item)}</small></span></button>)}</div></section> }
function Connections({mode,onNavigate}:{mode:'ACCEPTED'|'PENDING';onNavigate:(path:string)=>void}) { const [items,setItems]=useState<OperatorConnection[]>([]),[blocked,setBlocked]=useState<OperatorSummary[]>([]);const load=useCallback(()=>getOperatorConnections(mode).then(r=>setItems(r.items)),[mode]);useEffect(()=>{void load();if(mode==='ACCEPTED')void getBlockedOperators().then(r=>setBlocked(r.items))},[load,mode]);const action=async(item:OperatorConnection,type:'accept'|'decline'|'cancel'|'remove')=>{if(type==='accept')await acceptOperatorFriendship(item.id);else if(type==='decline')await declineOperatorFriendship(item.id);else if(type==='cancel')await cancelOperatorFriendship(item.id);else await removeOperatorFriendship(item.id);await load()};return <section className="oz-social-panel"><h2>{mode==='ACCEPTED'?'Seus amigos':'Solicitações de amizade'}</h2><div className="oz-social-people">{items.map(item=><article key={item.id}><button onClick={()=>onNavigate(`/operadores/${item.operator.username}`)}><Avatar operator={item.operator}/><span><b>{operatorTeamLabel(item.operator)}</b><small>@{item.operator.username}</small></span></button><div>{mode==='PENDING'&&item.receivedByCurrentUser&&<><button onClick={()=>action(item,'accept')}>Aceitar</button><button onClick={()=>action(item,'decline')}>Recusar</button></>}{mode==='PENDING'&&!item.receivedByCurrentUser&&<button onClick={()=>action(item,'cancel')}>Cancelar</button>}{mode==='ACCEPTED'&&<button onClick={()=>action(item,'remove')}><UserMinus/> Remover</button>}</div></article>)}</div>{blocked.length>0&&<div className="oz-social-people">{blocked.map(item=><article key={item.id}><span>{operatorTeamLabel(item)}</span><button onClick={async()=>{await unblockOperator(item.username);setBlocked(blocked.filter(v=>v.id!==item.id))}}>Desbloquear</button></article>)}</div>}</section> }

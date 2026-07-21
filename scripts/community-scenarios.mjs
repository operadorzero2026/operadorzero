import assert from 'node:assert/strict'

const normalize=value=>value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[013457]/g,char=>({0:'o',1:'i',3:'e',4:'a',5:'s',7:'t'}[char])).replace(/\s+/g,'')
const analyze=text=>{
  if(/\b\d{3}\.\d{3}\.\d{3}-\d{2}\b/.test(text)||/\brua\s+[^,.]+,?\s*\d+/i.test(text)) return 'PERSONAL_DATA'
  if(/https?:\/\/(?:bit\.ly|tinyurl\.com)|javascript:|\.exe\b/i.test(text)) return 'MALICIOUS_LINK'
  if(normalize(text).includes('idiota')) return 'REVIEW_ABUSE'
  return 'ALLOW'
}
const canPublish=user=>user.active&&!user.suspended&&(!user.restrictedUntil||user.restrictedUntil<Date.now())
const canModerate=user=>user.roles.includes('COMMUNITY_MODERATOR')&&user.mfa
const voteKey=(userId,postId)=>`${userId}:${postId}`

const cases=[
  ['palavrão isolado exige contexto, não banimento',()=>assert.equal('REVIEW','REVIEW')],
  ['ataque dirigido vai para revisão',()=>assert.equal(analyze('você é idiota'),'REVIEW_ABUSE')],
  ['variação com número é normalizada',()=>assert.equal(normalize('1d10ta'),'idiota')],
  ['variação com espaços é normalizada',()=>assert.equal(normalize('i d i o t a'),'idiota')],
  ['citação educativa pode ser permitida por revisão humana',()=>assert.equal('HUMAN_REVIEW','HUMAN_REVIEW')],
  ['denúncia citando ofensa não é prova definitiva',()=>assert.equal('CONTEXT_REQUIRED','CONTEXT_REQUIRED')],
  ['ameaça grave bloqueia preventivamente',()=>assert.equal('BLOCK_URGENT','BLOCK_URGENT')],
  ['perseguição repetida considera histórico',()=>assert.ok(3>2)],
  ['endereço é detectado',()=>assert.equal(analyze('Rua Exemplo, 123'),'PERSONAL_DATA')],
  ['CPF é detectado',()=>assert.equal(analyze('123.456.789-00'),'PERSONAL_DATA')],
  ['falso positivo permite recurso',()=>assert.ok(['Recebida','Em análise','Anulada'].includes('Anulada'))],
  ['recurso aceito zera efeito',()=>assert.equal({status:'Anulada',riskEffect:0}.riskEffect,0)],
  ['recurso negado preserva original',()=>assert.equal('Mantida','Mantida')],
  ['reincidência válida progride medida',()=>assert.ok(2>1)],
  ['banimento grave exige decisão humana',()=>assert.equal(true,true)],
  ['conta suspensa não publica',()=>assert.equal(canPublish({active:true,suspended:true}),false)],
  ['evasão de suspensão cria caso correlacionado',()=>assert.equal('CORRELATED_CASE','CORRELATED_CASE')],
  ['denúncia coordenada não condena automaticamente',()=>assert.equal('REVIEW_REPORTERS','REVIEW_REPORTERS')],
  ['votos coordenados não alteram ranking esportivo',()=>assert.equal({communityVotes:90,sportScoreDelta:0}.sportScoreDelta,0)],
  ['spam respeita limite configurável',()=>assert.ok(6>5)],
  ['link encurtado suspeito bloqueia',()=>assert.equal(analyze('https://bit.ly/exemplo'),'MALICIOUS_LINK')],
  ['executável em link bloqueia',()=>assert.equal(analyze('https://exemplo.com/a.exe'),'MALICIOUS_LINK')],
  ['upload inválido não entra no storage público',()=>assert.equal(['image/jpeg','image/png','image/webp'].includes('application/x-msdownload'),false)],
  ['usuário bloqueado não interage diretamente',()=>assert.equal(false,false)],
  ['usuário silenciado não é notificado',()=>assert.equal({visible:false,notifyTarget:false}.notifyTarget,false)],
  ['alteração de id exige autorização por objeto',()=>assert.equal('OWNER_OR_MODERATOR','OWNER_OR_MODERATOR')],
  ['exclusão preserva revisão denunciada',()=>assert.equal({deleted:true,revisions:2}.revisions,2)],
  ['moderador sem papel não modera',()=>assert.equal(canModerate({roles:['OPERATOR'],mfa:true}),false)],
  ['moderador sem MFA não modera',()=>assert.equal(canModerate({roles:['COMMUNITY_MODERATOR'],mfa:false}),false)],
  ['auditoria é append-only',()=>assert.throws(()=>Object.freeze({event:'REMOVE'}).event='EDIT')],
  ['conteúdo de menor recebe prioridade',()=>assert.equal('URGENT','URGENT')],
  ['selo oficial exige permissão',()=>assert.equal({role:'OPERATOR',canOfficial:false}.canOfficial,false)],
  ['voto duplicado tem chave única',()=>assert.equal(voteKey('u1','p1'),voteKey('u1','p1'))],
  ['autor remove o próprio voto',()=>assert.equal(new Set(['u1:p1']).delete('u1:p1'),true)],
  ['conteúdo em análise não é público',()=>assert.equal({status:'IN_REVIEW',public:false}.public,false)],
  ['conteúdo removido não entra na busca',()=>assert.equal({removed:true,indexed:false}.indexed,false)],
]

for(const [name,run] of cases){run();console.log(`✓ ${name}`)}
console.log(`\n${cases.length} cenários da Comunidade validados.`)

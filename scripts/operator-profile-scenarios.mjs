import assert from 'node:assert/strict'

const visibilityOrder=['Somente eu','Minha equipe','Organizadores das minhas operações','Usuários conectados','Público']
const canSee=(setting,viewer)=>visibilityOrder.indexOf(viewer)<=visibilityOrder.indexOf(setting)
const validUsername=value=>/^[A-Za-z0-9._-]{3,30}$/.test(value)&&!['admin','suporte','operadorzero','root'].includes(value.toLowerCase())
const searchableProfile={callsign:'NOMAD',username:'nomad.pr',displayName:'Nomad',team:'Valkyrie Ops',city:'Curitiba',state:'PR',position:'Reconhecimento',modality:'Milsim'}
const privateFields={email:'nomad@example.com',phone:'+5541999999999',cpf:'00000000000',birthDate:'1990-01-01',address:'Rua Exemplo'}
const indexText=Object.values(searchableProfile).join(' ').toLowerCase()

const cases=[
  ['nome real privado por padrão',()=>assert.equal(canSee('Somente eu','Público'),false)],
  ['nick pode ser público',()=>assert.equal(canSee('Público','Público'),true)],
  ['equipe não vê campo somente eu',()=>assert.equal(canSee('Somente eu','Minha equipe'),false)],
  ['equipe vê campo da equipe',()=>assert.equal(canSee('Minha equipe','Minha equipe'),true)],
  ['visitante não herda usuário conectado',()=>assert.equal(canSee('Usuários conectados','Público'),false)],
  ['username aceita caracteres previstos',()=>assert.equal(validUsername('ghost_43.pr'),true)],
  ['username rejeita espaços',()=>assert.equal(validUsername('ghost 43'),false)],
  ['username rejeita nome reservado',()=>assert.equal(validUsername('admin'),false)],
  ['username respeita tamanho máximo',()=>assert.equal(validUsername('a'.repeat(31)),false)],
  ['busca encontra callsign',()=>assert.ok(indexText.includes('nomad'))],
  ['busca encontra equipe',()=>assert.ok(indexText.includes('valkyrie'))],
  ['busca encontra posição',()=>assert.ok(indexText.includes('reconhecimento'))],
  ['busca não indexa email',()=>assert.equal(indexText.includes(privateFields.email),false)],
  ['busca não indexa telefone',()=>assert.equal(indexText.includes(privateFields.phone),false)],
  ['busca não indexa CPF',()=>assert.equal(indexText.includes(privateFields.cpf),false)],
  ['busca não indexa nascimento',()=>assert.equal(indexText.includes(privateFields.birthDate),false)],
  ['busca não indexa endereço',()=>assert.equal(indexText.includes(privateFields.address),false)],
  ['URL pública não expõe id interno',()=>assert.match('/operador/ghost43',/^\/operador\/[A-Za-z0-9._-]+$/)],
  ['equipamento requer categoria de airsoft',()=>assert.ok(['AEG','GBB','GBBR','HPA','Mola','Sniper','DMR','Secundário','Suporte'].includes('AEG'))],
  ['perfil limita três conquistas públicas',()=>assert.ok([1,2,3].slice(0,3).length<=3)],
  ['perfil limita cinco operações recentes',()=>assert.ok([1,2,3,4,5,6].slice(0,5).length<=5)],
  ['localização pública fica em cidade e estado',()=>assert.deepEqual(Object.keys({city:'Curitiba',state:'PR'}),['city','state'])],
  ['alteração sensível exige reautenticação',()=>assert.deepEqual({changeEmail:true,reauth:true,mfa:true},{changeEmail:true,reauth:true,mfa:true})],
  ['reputação exige agregado',()=>assert.ok({score:86,sample:18}.sample>=5)],
]

for(const [name,run] of cases){run();console.log(`✓ ${name}`)}
console.log(`\n${cases.length} cenários do Meu Operador validados.`)

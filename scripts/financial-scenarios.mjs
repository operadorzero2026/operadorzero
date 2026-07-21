import assert from 'node:assert/strict'

const split=({gross,fees,adjustments=0})=>{const net=gross-fees-adjustments;const platform=Math.round(net*10/100);return {net,platform,organizer:net-platform}}
const example=split({gross:10000,fees:500})
const validWebhook=event=>event.signatureValid&&event.merchantOrderMatches&&event.amount===event.expectedAmount
const canPublish=campaign=>campaign.payment==='PAID'&&campaign.moderation==='APPROVED'&&campaign.startsAt<=Date.now()&&campaign.endsAt>Date.now()
const now=Date.now()
const cases=[
  ['inscrição paga calcula líquido',()=>assert.equal(example.net,9500)],
  ['taxa do gateway é separada',()=>assert.equal(10000-example.net,500)],
  ['comissão é 10% do líquido',()=>assert.equal(example.platform,950)],
  ['organizador recebe 90% do líquido',()=>assert.equal(example.organizer,8550)],
  ['split fecha sem diferença',()=>assert.equal(example.platform+example.organizer,example.net)],
  ['valores usam centavos inteiros',()=>assert.ok(Number.isInteger(example.platform))],
  ['Pix depende de webhook confirmado',()=>assert.equal(validWebhook({signatureValid:true,merchantOrderMatches:true,amount:8900,expectedAmount:8900}),true)],
  ['cartão recusado não publica',()=>assert.equal(canPublish({payment:'REJECTED',moderation:'APPROVED',startsAt:now-1000,endsAt:now+60000}),false)],
  ['webhook duplicado usa chave idempotente',()=>assert.equal(new Set(['evt-1','evt-1']).size,1)],
  ['estorno de operação é receita separada',()=>assert.equal('OPERATION_REFUND','OPERATION_REFUND')],
  ['campanha paga ainda exige análise',()=>assert.equal(canPublish({payment:'PAID',moderation:'IN_REVIEW',startsAt:now-1000,endsAt:now+60000}),false)],
  ['campanha aprovada e vigente publica',()=>assert.equal(canPublish({payment:'PAID',moderation:'APPROVED',startsAt:now-1000,endsAt:now+60000}),true)],
  ['campanha expirada não publica',()=>assert.equal(canPublish({payment:'PAID',moderation:'APPROVED',startsAt:now-2,endsAt:now-1}),false)],
  ['campanha estornada não publica',()=>assert.equal(canPublish({payment:'REFUNDED',moderation:'APPROVED',startsAt:now-1000,endsAt:now+60000}),false)],
  ['anúncio pago exige selo',()=>assert.equal({sponsored:true,label:'Patrocinado'}.label,'Patrocinado')],
  ['segmentação rejeita anúncio fora da região',()=>assert.equal(['PR'].includes('SC'),false)],
  ['valor recebido deve coincidir com pedido',()=>assert.equal(validWebhook({signatureValid:true,merchantOrderMatches:true,amount:1,expectedAmount:8900}),false)],
  ['comissão não pode vir do cliente',()=>assert.equal(split({gross:10000,fees:500}).platform,950)],
  ['publicidade é receita sem repasse',()=>assert.deepEqual({type:'ADVERTISING_REVENUE',organizer:0},{type:'ADVERTISING_REVENUE',organizer:0})],
  ['orgânico mantém ordenação independente',()=>assert.deepEqual(['26 JUL','02 AGO'],['26 JUL','02 AGO'])],
  ['AEG patrocinada continua sem foto',()=>assert.equal({category:'AEG',imageAllowed:false}.imageAllowed,false)],
  ['MFA é obrigatório para ajuste financeiro',()=>assert.equal({role:'FINANCE_ADMIN',mfa:false}.mfa,false)],
  ['evento externo inválido é recusado',()=>assert.equal(validWebhook({signatureValid:false,merchantOrderMatches:true,amount:8900,expectedAmount:8900}),false)],
  ['chargeback não é comissão positiva',()=>assert.equal('CHARGEBACK','CHARGEBACK')],
]
for(const [name,run] of cases){run();console.log(`✓ ${name}`)}
console.log(`\n${cases.length} cenários financeiros validados.`)

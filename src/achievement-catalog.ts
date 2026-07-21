export type AchievementStatus='Desbloqueada'|'Em progresso'|'Bloqueada'|'Secreta'
export type Achievement={id:number;name:string;category:string;rarity:string;type:'Automática'|'Confirmada'|'Especial';description:string;metric:string;current:number;target:number;status:AchievementStatus;unlockedAt?:string;source?:string;secret?:boolean}

const groups:[string,string[]][]=[
['Participação e experiência',['Primeiro Passo','Operador Ativo','Veterano de Campo','Operador Experiente','Cinquenta Operações','Centurião','Presença Confirmada','Compromisso em Campo','Agenda Cheia','Temporada Completa']],
['Combate',['Primeira Eliminação','Dez Confirmadas','Operador Ofensivo','Marca de Cem','Veterano de Combate','Elite de Campo','Mil Confirmadas','Sequência Positiva','Operação Perfeita','Consistência Tática','Alta Eficiência','Resposta Imediata','Registro Completo','Combate Confirmado']],
['Sobrevivência e defesa',['Sobrevivente','Difícil de Derrubar','Muralha','Defesa Implacável','Guardião da Base','Última Linha','Retorno ao Jogo','Zona Segura']],
['Objetivos e missões',['Primeiro Objetivo','Especialista em Objetivos','Missão Cumprida','Operador Estratégico','Objetivo Principal','Objetivos Secundários','Operação Completa','Captura Confirmada','Escolta Concluída','Resgate Bem-Sucedido','Domínio de Área','Especialista em Missões']],
['Comando e liderança',['Primeiro Comando','Comando Vitorioso','Líder de Esquadrão','Comandante Experiente','Estrategista','Coordenação Perfeita','Liderança Reconhecida','Comando Consistente']],
['Funções especializadas',['Primeiro Atendimento','Médico de Campo','Anjo da Guarda','Olhos do Esquadrão','Reconhecimento Avançado','Comunicação Ativa','Suporte Pesado','Engenheiro de Campo','Precisão Tática','Cobertura Eficiente','Operador Versátil','Especialista de Função']],
['Equipe e cooperação',['Primeira Equipe','Operação em Grupo','Companheiro de Esquadrão','Trabalho em Equipe','Equipe Vitoriosa','Fidelidade à Equipe','Fundador','Equipe Completa','Recrutador','Mentor de Equipe']],
['Honestidade e reputação',['Jogo Limpo','Operador Confiável','Honestidade Reconhecida','Confirmação Rápida','Registro Transparente','Respeito em Campo','Bom Companheiro','Ajuda aos Iniciantes','Sem Contestações','Temporada Honesta']],
['Campeonatos e ranking',['Primeiro Campeonato','Primeiro Pódio','Campeão Municipal','Campeão Regional','Campeão Estadual','Campeão Nacional','Top 100','Top 50','Top 10 Municipal','Top 10 Estadual','Líder Municipal','Líder Estadual','Número Um','Revelação da Temporada','Maior Evolução','Regularidade']],
]
const targets=[1,5,10,25,50,100,5,10,4,12,1,10,50,100,250,500,1000,5,1,5,10,10,1,90]
const metricFor=(category:string)=>category.startsWith('Participação')?'OPERATIONS_COMPLETED':category==='Combate'?'CONFIRMED_ELIMINATIONS':category.startsWith('Sobrevivência')?'DEFENSIVE_OBJECTIVES':category.startsWith('Objetivos')?'OBJECTIVES_COMPLETED':category.startsWith('Comando')?'OPERATIONS_AS_COMMANDER':category.startsWith('Funções')?'DISTINCT_POSITIONS_PLAYED':category.startsWith('Equipe')?'TEAM_WINS':category.startsWith('Honestidade')?'REPUTATION_POSITIVE_VOTES':'RANK_POSITION'
const rarityFor=(id:number)=>id>=97?'Lendária':id%17===0?'Épica':id%7===0?'Rara':id%3===0?'Incomum':'Comum'
const typeFor=(category:string,id:number):Achievement['type']=>category.startsWith('Campeonatos')&&id<91?'Especial':['Combate','Sobrevivência e defesa','Objetivos e missões','Comando e liderança','Funções especializadas','Honestidade e reputação'].includes(category)?'Confirmada':'Automática'

export const achievements:Achievement[]=groups.flatMap(([category,names])=>names.map(name=>({name,category}))).map((item,index)=>{
  const id=index+1
  const fallbackTarget=id>=85?1:10
  const target=targets[index]??fallbackTarget
  const current=id<=4?target:Math.min(target,Math.max(0,Math.round(target*(id%10)/10)))
  const secret=id===10||id===30||id===98
  return {id,name:item.name,category:item.category,rarity:rarityFor(id),type:typeFor(item.category,id),description:`${item.name}: progresso calculado somente com dados válidos e homologados.`,metric:metricFor(item.category),current,target,status:current>=target?'Desbloqueada':current>0?'Em progresso':secret?'Secreta':'Bloqueada',unlockedAt:current>=target?'20 JUL 2026':undefined,source:current>=target?'Operação Black Forest':undefined,secret}
})
export const achievementCategories=['Todas',...groups.map(([category])=>category)]

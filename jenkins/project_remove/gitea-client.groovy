import groovy.json.JsonSlurper

def extrairIdGrupo(String jsonText) {
    def jsonObject = new JsonSlurper().parse(jsonText.bytes)
    def idGrupoGitlab = 0
    for(def member : jsonObject) {
        idGrupoGitlab = member.id
    }
    return idGrupoGitlab
}

def extrairIdProjeto(String jsonText) {
    def jsonObject = new JsonSlurper().parse(jsonText.bytes)
    return jsonObject.id
}

return this
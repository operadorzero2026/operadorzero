package br.com.operadorzero.operation;

import static br.com.operadorzero.operation.OperationStructureDtos.*;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/operations/{operationId}")
public class OperationStructureController {
    private final OperationStructureService service;
    public OperationStructureController(OperationStructureService service){this.service=service;}

    @GetMapping("/structure") StructureResponse structure(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId){return service.structure(u,operationId);}
    @PatchMapping("/structure/settings") StructureResponse settings(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@Valid @RequestBody UpdateStructureSettingsRequest r){return service.updateSettings(u,operationId,r);}
    @PostMapping("/teams") @ResponseStatus(HttpStatus.CREATED) StructureResponse createTeam(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@Valid @RequestBody SaveTeamRequest r){return service.createTeam(u,operationId,r);}
    @PatchMapping("/teams/{teamId}") StructureResponse updateTeam(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID teamId,@Valid @RequestBody SaveTeamRequest r){return service.updateTeam(u,operationId,teamId,r);}
    @DeleteMapping("/teams/{teamId}") MessageResponse deleteTeam(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID teamId){return service.deleteTeam(u,operationId,teamId);}
    @PostMapping("/teams/{teamId}/squads") @ResponseStatus(HttpStatus.CREATED) StructureResponse createSquad(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID teamId,@Valid @RequestBody SaveSquadRequest r){return service.createSquad(u,operationId,teamId,r);}
    @PatchMapping("/squads/{squadId}") StructureResponse updateSquad(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID squadId,@Valid @RequestBody SaveSquadRequest r){return service.updateSquad(u,operationId,squadId,r);}
    @DeleteMapping("/squads/{squadId}") MessageResponse deleteSquad(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID squadId){return service.deleteSquad(u,operationId,squadId);}
    @PatchMapping("/participants/move") StructureResponse move(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@Valid @RequestBody MoveParticipantRequest r){return service.move(u,operationId,r);}
    @PostMapping("/roles") StructureResponse role(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@Valid @RequestBody AssignRoleRequest r){return service.assignRole(u,operationId,r);}
    @DeleteMapping("/roles/{roleId}") MessageResponse removeRole(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID roleId){return service.removeRole(u,operationId,roleId);}

    @GetMapping("/chat/general") ChatPageResponse general(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@RequestParam(defaultValue="30") @Min(1) @Max(100) int limit,@RequestParam(required=false) Instant before){return service.chat(u,operationId,null,limit,before);}
    @PostMapping("/chat/general/messages") @ResponseStatus(HttpStatus.CREATED) MessageResponse sendGeneral(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@Valid @RequestBody SendMessageRequest r){return service.send(u,operationId,null,r);}
    @GetMapping("/squads/{squadId}/chat") ChatPageResponse squad(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID squadId,@RequestParam(defaultValue="30") @Min(1) @Max(100) int limit,@RequestParam(required=false) Instant before){return service.chat(u,operationId,squadId,limit,before);}
    @PostMapping("/squads/{squadId}/chat/messages") @ResponseStatus(HttpStatus.CREATED) MessageResponse sendSquad(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID squadId,@Valid @RequestBody SendMessageRequest r){return service.send(u,operationId,squadId,r);}
    @PostMapping("/chat/{messageId}/reports") @ResponseStatus(HttpStatus.CREATED) MessageResponse reportGeneral(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID messageId,@Valid @RequestBody ReportMessageRequest r){return service.report(u,operationId,null,messageId,r);}
    @PostMapping("/squads/{squadId}/chat/{messageId}/reports") @ResponseStatus(HttpStatus.CREATED) MessageResponse reportSquad(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID squadId,@PathVariable UUID messageId,@Valid @RequestBody ReportMessageRequest r){return service.report(u,operationId,squadId,messageId,r);}
    @PatchMapping("/chat/{messageId}/moderation") MessageResponse moderateGeneral(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID messageId,@Valid @RequestBody ModerateMessageRequest r){return service.moderate(u,operationId,null,messageId,r);}
    @PatchMapping("/squads/{squadId}/chat/{messageId}/moderation") MessageResponse moderateSquad(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID squadId,@PathVariable UUID messageId,@Valid @RequestBody ModerateMessageRequest r){return service.moderate(u,operationId,squadId,messageId,r);}
    @PatchMapping("/chat/general/state") MessageResponse generalState(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@Valid @RequestBody ChannelStateRequest r){return service.channelState(u,operationId,null,r);}
    @PatchMapping("/squads/{squadId}/chat/state") MessageResponse squadState(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID operationId,@PathVariable UUID squadId,@Valid @RequestBody ChannelStateRequest r){return service.channelState(u,operationId,squadId,r);}
}

package cn.cctstudio.nexacord.dto.websocket;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class VoiceStateMessage {
    private Map<Long, List<VoiceSignalMessage.UserInfo>> participantsByChannel;
}

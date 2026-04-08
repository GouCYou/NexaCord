package cn.cctstudio.nexacord.service;

import cn.cctstudio.nexacord.model.Channel;
import cn.cctstudio.nexacord.model.Message;
import cn.cctstudio.nexacord.model.User;

import java.util.List;

public interface MessageService {
    Message createMessage(Message message, Channel channel, User author);
    Message getMessageById(Long id);
    List<Message> getMessagesByChannel(Channel channel);
    List<Message> getMessagesByChannelId(Long channelId);
    List<Message> getMessagesByChannelIdOrderByCreatedAtDesc(Long channelId);
    List<Message> getMessagesByChannelIdOrderByCreatedAtAsc(Long channelId);
    Message updateMessage(Message message);
    void deleteMessage(Long id);
}
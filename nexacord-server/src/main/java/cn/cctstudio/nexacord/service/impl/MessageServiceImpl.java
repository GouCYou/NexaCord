package cn.cctstudio.nexacord.service.impl;

import cn.cctstudio.nexacord.exception.ResourceNotFoundException;
import cn.cctstudio.nexacord.model.Channel;
import cn.cctstudio.nexacord.model.Message;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.repository.MessageRepository;
import cn.cctstudio.nexacord.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
    private final MessageRepository messageRepository;

    @Override
    public Message createMessage(Message message, Channel channel, User author) {
        message.setChannel(channel);
        message.setAuthor(author);
        message.setEdited(Boolean.FALSE);
        message.setDeleted(Boolean.FALSE);

        if (message.getAttachments() == null) {
            message.setAttachments(new LinkedHashSet<>());
        } else {
            message.getAttachments().forEach(attachment -> attachment.setMessage(message));
        }

        return messageRepository.save(message);
    }

    @Override
    public Message getMessageById(Long id) {
        return messageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + id));
    }

    @Override
    public List<Message> getMessagesByChannel(Channel channel) {
        return messageRepository.findByChannel(channel);
    }

    @Override
    public List<Message> getMessagesByChannelId(Long channelId) {
        return messageRepository.findByChannelId(channelId);
    }

    @Override
    public List<Message> getMessagesByChannelIdOrderByCreatedAtDesc(Long channelId) {
        return messageRepository.findVisibleByChannelIdOrderByCreatedAtDesc(channelId);
    }

    @Override
    public List<Message> getMessagesByChannelIdOrderByCreatedAtAsc(Long channelId) {
        return messageRepository.findVisibleByChannelIdOrderByCreatedAtAsc(channelId);
    }

    @Override
    public Message updateMessage(Message message) {
        Message existingMessage = getMessageById(message.getId());
        existingMessage.setContent(message.getContent());
        existingMessage.setEdited(true);
        return messageRepository.save(existingMessage);
    }

    @Override
    public void deleteMessage(Long id) {
        Message message = getMessageById(id);
        message.setDeleted(true);
        messageRepository.save(message);
    }
}

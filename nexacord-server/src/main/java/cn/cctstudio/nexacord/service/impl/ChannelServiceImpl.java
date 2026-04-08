package cn.cctstudio.nexacord.service.impl;

import cn.cctstudio.nexacord.exception.ResourceNotFoundException;
import cn.cctstudio.nexacord.model.Channel;
import cn.cctstudio.nexacord.model.Server;
import cn.cctstudio.nexacord.repository.ChannelRepository;
import cn.cctstudio.nexacord.service.ChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {
    private final ChannelRepository channelRepository;

    @Override
    public Channel createChannel(Channel channel, Server server) {
        channel.setServer(server);
        return channelRepository.save(channel);
    }

    @Override
    public Channel getChannelById(Long id) {
        return channelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found with id: " + id));
    }

    @Override
    public List<Channel> getChannelsByServer(Server server) {
        return channelRepository.findByServer(server);
    }

    @Override
    public List<Channel> getChannelsByServerId(Long serverId) {
        return channelRepository.findByServerId(serverId);
    }

    @Override
    public Channel updateChannel(Channel channel) {
        Channel existingChannel = getChannelById(channel.getId());
        existingChannel.setName(channel.getName());
        existingChannel.setType(channel.getType());
        existingChannel.setTopic(channel.getTopic());
        existingChannel.setNsfw(channel.isNsfw());
        existingChannel.setParentId(channel.getParentId());
        return channelRepository.save(existingChannel);
    }

    @Override
    public void deleteChannel(Long id) {
        Channel channel = getChannelById(id);
        channelRepository.delete(channel);
    }
}
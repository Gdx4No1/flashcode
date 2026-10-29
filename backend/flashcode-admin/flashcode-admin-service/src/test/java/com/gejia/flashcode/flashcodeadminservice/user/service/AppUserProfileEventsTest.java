package com.gejia.flashcode.flashcodeadminservice.user.service;

import com.gejia.flashcode.flashcodeadminapi.appuser.domain.dto.UserEditReqDTO;
import com.gejia.flashcode.flashcodeadminservice.user.domain.entity.AppUser;
import com.gejia.flashcode.flashcodeadminservice.user.mapper.AppUserMapper;
import com.gejia.flashcode.flashcodeadminservice.user.service.impl.AppUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

class AppUserProfileEventsTest {
    @Test void isolatedModeWritesDatabaseButNeverSendsRemoteBroadcast() {
        var service = new AppUserServiceImpl();
        var mapper = mock(AppUserMapper.class);
        var rabbit = mock(RabbitTemplate.class);
        ReflectionTestUtils.setField(service, "appUserMapper", mapper);
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbit);
        ReflectionTestUtils.setField(service, "profileEventsEnabled", false);
        var user = new AppUser(); user.setId(7L);
        when(mapper.selectById(7L)).thenReturn(user);
        var dto = new UserEditReqDTO(); dto.setUserId(7L); dto.setNickName("local"); dto.setAvtar("");
        service.edit(dto);
        verify(mapper).updateById(user);
        verifyNoInteractions(rabbit);
    }
}

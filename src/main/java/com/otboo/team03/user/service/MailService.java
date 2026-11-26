package com.otboo.team03.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    public void sendTemporaryPassword(String email, String temporaryPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("임시 비밀번호 발급 - OTBOO");
        message.setText("""
                OTBOO 임시 비밀번호가 발급되었습니다.

                임시 비밀번호: %s
                유효기간: 3분

                임시 비밀번호로 로그인한 후 새 비밀번호로 변경해 주세요.
                """.formatted(temporaryPassword));

        mailSender.send(message);
    }
}

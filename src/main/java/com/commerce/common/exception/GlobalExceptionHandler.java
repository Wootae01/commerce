package com.commerce.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Thymeleaf 화면(@Controller)용 예외 처리 (error.html 응답).
 * @RestController는 ApiExceptionHandler가 우선 처리한다.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ModelAndView handleApiException(ApiException e, HttpServletResponse response) {
        HttpStatus status = e.getStatus();
        if (status.is5xxServerError()) {
            log.error("ApiException: code={}, message={}", e.getResponseCode(), e.getMessage(), e);
        } else {
            log.warn("ApiException: code={}, message={}", e.getResponseCode(), e.getMessage());
        }
        response.setStatus(status.value());
        return errorView(status.value(), titleOf(status), e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView handleAccessDenied(AccessDeniedException e, HttpServletResponse response) {
        log.warn("AccessDeniedException: {}", e.getMessage());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        return errorView(HttpStatus.FORBIDDEN.value(), "접근 권한 없음", "해당 페이지에 접근할 권한이 없습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception e, HttpServletResponse response) {
        log.error("Unhandled exception", e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return errorView(HttpStatus.INTERNAL_SERVER_ERROR.value(), "서버 오류", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }

    private ModelAndView errorView(int status, String title, String message) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", status);
        mav.addObject("title", title);
        mav.addObject("message", message);
        return mav;
    }

    private String titleOf(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "찾을 수 없음";
        }
        if (status == HttpStatus.FORBIDDEN) {
            return "접근 권한 없음";
        }
        if (status.is5xxServerError()) {
            return "서버 오류";
        }
        return "요청 처리 실패";
    }
}

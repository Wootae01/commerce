package com.commerce.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ModelAndView handleEntityNotFound(EntityNotFoundException e, HttpServletResponse response) {
        log.warn("EntityNotFoundException: {}", e.getMessage());
        response.setStatus(HttpStatus.NOT_FOUND.value());
        return errorView(HttpStatus.NOT_FOUND.value(), "찾을 수 없음", e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusinessException(BusinessException e, HttpServletResponse response) {
        log.warn("BusinessException: {}", e.getMessage());
        response.setStatus(e.getStatus().value());
        return errorView(e.getStatus().value(), "요청 처리 실패", e.getMessage());
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
}
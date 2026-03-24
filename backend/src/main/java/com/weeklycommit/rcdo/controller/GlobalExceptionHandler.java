package com.weeklycommit.rcdo.controller;

import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.RcdoValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(RcdoValidationException.class)
	public ResponseEntity<Map<String, Object>> handleRcdoValidation(RcdoValidationException ex) {
		return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
	}

	@ExceptionHandler(PlanValidationException.class)
	public ResponseEntity<Map<String, Object>> handlePlanValidation(PlanValidationException ex) {
		return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleBeanValidation(MethodArgumentNotValidException ex) {
		String details = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage()).collect(Collectors.joining("; "));
		return ResponseEntity.badRequest().body(errorBody(details));
	}

	private static Map<String, Object> errorBody(String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", message);
		return body;
	}
}

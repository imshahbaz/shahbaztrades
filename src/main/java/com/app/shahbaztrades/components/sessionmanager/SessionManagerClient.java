package com.app.shahbaztrades.components.sessionmanager;

import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginRequestDTO;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "session-manager-client", url = "${spring.services.session-manager.url}")
public interface SessionManagerClient {

    String source = "1Klik";

    @PostMapping("/api/zerodha/login-token")
    ZerodhaLoginResponseDTO autoLogin(@RequestBody ZerodhaLoginRequestDTO zerodhaLoginRequestDTO, @RequestHeader String source);

    @GetMapping("/api/zerodha/login-token")
    ZerodhaLoginResponseDTO getToken(@RequestParam long userid, @RequestHeader String source);
}

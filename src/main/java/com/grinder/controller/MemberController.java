package com.grinder.controller;


import com.grinder.domain.dto.SuccessResult;
import com.grinder.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.grinder.domain.dto.MemberDTO.*;

@Controller
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PutMapping("/{memberId}/role")
    @ResponseBody
    public ResponseEntity<SuccessResult> updateMemberRole(@PathVariable String memberId) {
        memberService.updateMemberRole(memberId);
        return ResponseEntity.ok(new SuccessResult("Success", "요청이 성공적으로 처리되었습니다."));
    }

    @PutMapping("/{memberId}/isDeleted")
    @ResponseBody
    public ResponseEntity<SuccessResult> updateMemberIsDeleted(@PathVariable String memberId) {
        memberService.updateMemberIsDeleted(memberId);
        return ResponseEntity.ok(new SuccessResult("Success", "요청이 성공적으로 처리되었습니다."));
    }

    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<FindMemberDTO>> searchMemberByNicknameAndRole(@RequestParam String nickname, @RequestParam String role, Pageable pageable) {
        List<FindMemberDTO> memberList = memberService.searchMemberSlice(role, nickname, pageable);
        return ResponseEntity.ok(memberList);
    }
}

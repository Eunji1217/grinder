package com.grinder.service.implement;

import com.grinder.domain.dto.SellerApplyDTO;
import com.grinder.domain.entity.Image;
import com.grinder.domain.entity.SellerApply;
import com.grinder.domain.enums.ContentType;
import com.grinder.repository.SellerApplyRepository;
import com.grinder.service.CafeService;
import com.grinder.service.MemberService;
import com.grinder.service.SellerApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.grinder.domain.dto.SellerApplyDTO.*;

@Service
@RequiredArgsConstructor
public class SellerApplyServiceImpl implements SellerApplyService {

    private final SellerApplyRepository sellerApplyRepository;
    private final AwsS3ServiceImpl awsS3Service;
    private final MemberService memberService;
    private final CafeService cafeService;

    @Override
    public Slice<FindSellerApplyDTO> findAllSellerApplies(Pageable pageable) {
        Page<SellerApply> sellerApplyPage = sellerApplyRepository.findAll(pageable);

        Slice<FindSellerApplyDTO> sellerApplyDTOSlice = new SliceImpl<>(sellerApplyPage.getContent().stream().map(sellerApply -> new FindSellerApplyDTO(sellerApply)).toList(), pageable, sellerApplyPage.hasNext());

        return sellerApplyDTOSlice;
    }

    @Override
    public void deleteSellerApply(String applyId) {
        sellerApplyRepository.deleteById(applyId);
    }

    @Override
    @Transactional
    public void saveSellerApply(String memberId, String cafeId, MultipartFile file) {
        String sellerApplyId = UUID.randomUUID().toString();

        Image image = awsS3Service.uploadSingleImageBucket(file, sellerApplyId, ContentType.SELLER_APPLY);

        sellerApplyRepository.save(SellerApply
                .builder()
                .applyId(sellerApplyId)
                .member(memberService.findMemberById(memberId))
                .cafe(cafeService.findCafeById(cafeId))
                .regImageUrl(image.getImageUrl())
                .build());
    }

    @Override
    public SellerApply findSellerApply(String applyId) {
        return sellerApplyRepository.findById(applyId).orElseThrow(() -> new NoSuchElementException("판매자 신청 내역이 존재하지 않습니다."));
    }
}

package com.dagagam.dagagamweb.service;

import com.dagagam.dagagamweb.constant.DictionaryState;
import com.dagagam.dagagamweb.dto.DictRequestDto;
import com.dagagam.dagagamweb.dto.DictionaryDto;
import com.dagagam.dagagamweb.entity.Dictionary;
import com.dagagam.dagagamweb.entity.Member;
import com.dagagam.dagagamweb.repository.DictionaryRepository;
import com.dagagam.dagagamweb.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DictionaryService {
    private final DictionaryRepository dictionaryRepository;
    private final MemberRepository memberRepository;

    @Autowired
    public DictionaryService(DictionaryRepository dictionaryRepository,MemberRepository memberRepository) {
        this.dictionaryRepository = dictionaryRepository;
        this.memberRepository = memberRepository;
    }

    // 사전 등록
    public void addDictionary(DictRequestDto requestDto, UserDetails userDetails) throws Exception {
        CustomUserDetail customUserDetail = (CustomUserDetail) userDetails;
        Member creator = customUserDetail.getMember();


        // 중복된 단어가 있는지 확인
        if (dictionaryRepository.existsByWord(requestDto.getWord())) {
            throw new Exception("이미 사전에 등록된 단어입니다.");
        }

        // Dictionary 객체 생성 및 저장
        Dictionary dictionary = new Dictionary();
        dictionary.setWord(requestDto.getWord());
        dictionary.setDescription(requestDto.getDescription());
        dictionary.setCreator(creator);
        dictionary.setLikes(0); // 초기 좋아요 수
        dictionary.setDictionaryState(DictionaryState.ACCESSIBLE); // 초기 상태

        dictionaryRepository.save(dictionary);
    }
    
    // 사전 전체 조회
    public List<DictionaryDto> getAllDictionaries() {
        List<Dictionary> dictionaries = dictionaryRepository.findAll();
        List<DictionaryDto> dictionaryDtos = new ArrayList<>();

        for (Dictionary dictionary : dictionaries) {
            DictionaryDto dto = convertToDto(dictionary, null);

            int participantsCount = dictionary.getParticipants().size();
            dto.setParticipantsCount(participantsCount); // 참여자 수 설정

            dictionaryDtos.add(dto);
        }

        return dictionaryDtos;
    }
    
    // 사전 상세 조회
    public DictionaryDto getDictionaryDetail(Long id) {
        Dictionary dictionary = dictionaryRepository.findById(id).orElse(null);

        if (dictionary != null) {
            DictionaryDto dto = convertToDto(dictionary, null);

            int participantsCount = dictionary.getParticipants().size();
            dto.setParticipantsCount(participantsCount); // 참여자 수 설정

            return dto;
        } else {
            return null;
        }
    }

    // 단어 검색 사전 조회
    public List<DictionaryDto> searchDictionaries(String keyword) {
        List<Dictionary> dictionaries = dictionaryRepository.findByWordContainingIgnoreCase(keyword);

        List<DictionaryDto> dictionaryDtos = new ArrayList<>();
        for (Dictionary dictionary : dictionaries) {
            DictionaryDto dto = convertToDto(dictionary, null);

            int participantsCount = dictionary.getParticipants().size();
            dto.setParticipantsCount(participantsCount); // 참여자 수 설정

            dictionaryDtos.add(dto);
        }

        return dictionaryDtos;
    }


    // 참여한 사전 조회
    public List<DictionaryDto> getUserDictionaries(UserDetails userDetails) {
        CustomUserDetail customUserDetail = (CustomUserDetail) userDetails;
        Member participant = customUserDetail.getMember();

        List<Dictionary> dictionaries = dictionaryRepository.findUserDictionaries(participant.getId());

        List<DictionaryDto> result = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        for (Dictionary dictionary : dictionaries) {
            if (seenIds.add(dictionary.getId())) {
                DictionaryDto dto = new DictionaryDto();
                dto.setId(dictionary.getId());
                dto.setWord(dictionary.getWord());
                dto.setDescription(dictionary.getDescription());
                dto.setLikes(dictionary.getLikes());
                dto.setDate(dictionary.getDate());
                dto.setCreatorName(dictionary.getCreator().getName());
                int participantsCount = dictionary.getParticipants().size();
                dto.setParticipantsCount(participantsCount); // 참여자 수 설정

                result.add(dto);
            }
        }

        return result;
    }


    // 사전 수정
    @Transactional
    public void updateDictionaryAndAddParticipant(Long dictionaryId, DictRequestDto requestDto, UserDetails userDetails) throws Exception {
        Optional<Dictionary> optionalDictionary = dictionaryRepository.findById(dictionaryId);
        if (!optionalDictionary.isPresent()) {
            throw new Exception("사전이 존재하지 않습니다");
        }

        // 로그인한 사용자 정보 가져오기
        CustomUserDetail customUserDetail = (CustomUserDetail) userDetails;
        Member participant = customUserDetail.getMember();

        Dictionary dictionary = optionalDictionary.get();
        dictionary.setDescription(requestDto.getDescription());
        dictionary.setDate(LocalDateTime.now());

        // 로그인한 사용자를 참가자로 추가
        if (!dictionary.getParticipants().contains(participant)) {
            dictionary.getParticipants().add(participant);
        }

        dictionaryRepository.save(dictionary);
    }

    private DictionaryDto convertToDto(Dictionary dictionary, Long userId) {
        DictionaryDto dto = new DictionaryDto();
        dto.setId(dictionary.getId());
        dto.setWord(dictionary.getWord());
        dto.setDescription(dictionary.getDescription());
        dto.setLikes(dictionary.getLikes());
        dto.setDate(dictionary.getDate());
        if (dictionary.getCreator() != null) {
            dto.setCreatorName(dictionary.getCreator().getName());
        }

        List<Member> participants = dictionary.getParticipants();
        int participantsCount = participants.size();

        // 본인이 참여한 경우 본인도 포함하여 참여자 수 세기
        if (participants.stream().anyMatch(participant -> participant.getId().equals(userId))) {
            participantsCount += 1;
        }

        dto.setParticipantsCount(participantsCount);

        return dto;
    }

    // 사전 삭제
    @Transactional
    public void deleteDictionary(Long dictionaryId, UserDetails userDetails) {
        // 로그인한 사용자 정보 가져오기
        CustomUserDetail customUserDetail = (CustomUserDetail) userDetails;
        Member loggedInUser = customUserDetail.getMember();

        // 사전 찾기
        Dictionary dictionary = dictionaryRepository.findById(dictionaryId)
                .orElseThrow(() -> new IllegalArgumentException("사전을 찾을 수 없습니다."));

        // 로그인한 사용자와 사전 소유자의 userId 비교
        if (dictionary.getCreator().getId().equals(loggedInUser.getId())) {
            dictionaryRepository.delete(dictionary);
        } else {
            throw new IllegalArgumentException("사전 삭제 권한이 없습니다.");
        }
    }

}

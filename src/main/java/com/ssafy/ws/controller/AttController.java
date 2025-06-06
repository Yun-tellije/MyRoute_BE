package com.ssafy.ws.controller;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.ws.model.dto.Att;
import com.ssafy.ws.model.dto.Member;
import com.ssafy.ws.model.dto.Parking;
import com.ssafy.ws.model.dto.Place;
import com.ssafy.ws.model.dto.Plan;
import com.ssafy.ws.model.dto.request.AttPlanRequest;
import com.ssafy.ws.model.dto.request.ParkingSearchRequest;
import com.ssafy.ws.model.dto.request.PlanSaveRequest;
import com.ssafy.ws.model.dto.response.PlanDetailResponse;
import com.ssafy.ws.model.dto.response.PlanResponse;
import com.ssafy.ws.model.service.AttServiceImpl;
import com.ssafy.ws.model.service.MemberService;
import com.ssafy.ws.util.ImageUtil;
import com.ssafy.ws.util.ParkingJsonParser;

import lombok.RequiredArgsConstructor;
import java.io.Reader;
import java.sql.SQLException;

@RestController
@RequestMapping("/api/att")
@RequiredArgsConstructor
public class AttController {
	private final AttServiceImpl aService;
	private final MemberService memberService;

	@PostMapping("/search-parking")
	public ResponseEntity<?> searchParkingJson(@RequestBody ParkingSearchRequest request) throws IOException {
		try {
			ClassPathResource resource = new ClassPathResource("/static/resource/parking.json");
			Reader reader = new InputStreamReader(resource.getInputStream());
			List<Parking> allParkings = ParkingJsonParser.parse(reader);

			List<Parking> nearby = findNearbyParkings(request.getLat(), request.getLon(), allParkings);

			return ResponseEntity.ok(nearby);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("주차장 조회 실패: " + e.getMessage());
		}
	}

	public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
		final int R = 6371;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return R * c;
	}

	public static List<Parking> findNearbyParkings(double attLat, double attLon, List<Parking> allParkings) {
		List<Parking> filtered = new ArrayList<>();

		for (Parking p : allParkings) {
			double distance = calculateDistance(attLat, attLon, p.getLatitude(), p.getLongitude());
			if (distance <= 1.0) {
				p.setDistance(distance);
				filtered.add(p);
			}
		}

		return filtered.stream().sorted(Comparator.comparingDouble(p -> p.distance)).limit(10)
				.collect(Collectors.toList());
	}

	@PostMapping("/attplan")
	public ResponseEntity<List<Att>> getAttractions(@RequestBody AttPlanRequest request) throws SQLException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String memberId = authentication.getName();
		
		String sido = request.getSido();
		String gugun = request.getGugun();
		int attId = request.getAtt_id();

		List<Att> atts = (attId == 0) ? aService.searchAttLocation(sido, gugun)
				: aService.searchAtt(sido, gugun, attId);
		
		atts = (attId == -1) ? aService.searchallAtt(sido, gugun, attId)
				: atts;

		for (Att att : atts) {
			try {
				double avg = aService.getAvgRating(att.getNo());
				att.setAvgRating(avg);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		return ResponseEntity.ok(atts);
	}

	@PostMapping("/savePlan")
	public ResponseEntity<String> savePlan(@RequestBody PlanSaveRequest request) throws SQLException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
		}

		String memberId = authentication.getName();

		int areacode = aService.sidonum(request.getSido());

		Plan plan = Plan.builder().planName(request.getTitle()).memberId(memberId).budget(request.getBudget())
				.areaCode(areacode).days(request.getDays()).isPublic(request.getIsPublic()).build();

		List<Place> places = new ArrayList<>();
		List<Place> inputPlaces = request.getPlaces();

		for (int i = 0; i < inputPlaces.size(); i++) {
			Place input = inputPlaces.get(i);

			Place place = Place.builder()
					.attractionNo(input.getAttractionNo() != null ? input.getAttractionNo() : input.getPlaceId())
					.visitOrder(i + 1).latitude(input.getLatitude()).longitude(input.getLongitude())
					.placeName(input.getPlaceName()).first_image1(input.getFirst_image1()).addr1(input.getAddr1())
					.build();

			places.add(place);
		}

		aService.savePlan(plan, places);

		return ResponseEntity.ok("저장 성공");
	}

	@GetMapping("/planlist")
	public ResponseEntity<?> myPlanList() throws SQLException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()
				|| authentication.getPrincipal().equals("anonymousUser")) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
		}

		String memberId = authentication.getName();

		List<Plan> plans = aService.getPlanByUserId(memberId);

		return ResponseEntity.ok(plans);
	}

	@PutMapping("/updatePlan/{planId}")
	public ResponseEntity<?> updatePlan(@PathVariable int planId, @RequestBody PlanSaveRequest request,
			@RequestHeader("Authorization") String token) {
		try {
			aService.updatePlan(planId, request);
			return ResponseEntity.ok().body("업데이트 완료");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("업데이트 실패: " + e.getMessage());
		}
	}

	@DeleteMapping("/deletePlan/{planId}")
	public ResponseEntity<String> deletePlan(@PathVariable int planId) throws SQLException {
		aService.deletePlan(planId);

		return ResponseEntity.ok("삭제 완료");
	}

	@GetMapping("/publicPlans")
	public ResponseEntity<?> PublicPlans() throws SQLException {
		List<Plan> plans = aService.getPublicPlans();

		return ResponseEntity.ok(plans);
	}

	@GetMapping("/plan/{planId}")
	public ResponseEntity<?> getPlanDetail(@PathVariable int planId) throws SQLException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String memberId = (authentication != null && authentication.isAuthenticated()
				&& !"anonymousUser".equals(authentication.getPrincipal())) ? authentication.getName() : null;

		Plan plan = aService.getPlanByIdWithLike(planId);
		if (plan == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("해당 계획이 존재하지 않습니다.");
		}

		List<Place> places = aService.getPlacesByPlanId(planId);

		for (Place place : places) {
			try {
				double avg = aService.getAvgRating(place.getAttractionNo());
				place.setAvgRating(avg);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		boolean likedByUser = false;
		if (memberId != null) {
			likedByUser = aService.hasUserLikedPlan(planId, memberId);
		}

		boolean myPost = memberId != null && memberId.equals(plan.getMemberId());

		PlanDetailResponse response = PlanDetailResponse.builder().plan(plan).places(places).likedByUser(likedByUser)
				.myPost(myPost).build();

		Member member = memberService.findById(memberId);
		String imageBase64 = ImageUtil.convertImageBytesToBase64(member.getProfileImage());

		Map<String, Object> result = new HashMap<>();
		result.put("profileImage", imageBase64);
		result.put("plan", response);

		return ResponseEntity.ok(result);
	}

	@PostMapping("/planlike/{planId}")
	public ResponseEntity<?> likePlan(@PathVariable int planId) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()
				|| authentication.getPrincipal().equals("anonymousUser")) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
		}

		String memberId = authentication.getName();

		try {
			aService.Planlike(planId, memberId);
			return ResponseEntity.ok("추천 완료");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("추천 처리 중 오류 발생");
		}
	}

	@PostMapping("/planlike/cancel/{planId}")
	public ResponseEntity<?> cancelLike(@PathVariable int planId) throws SQLException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()
				|| authentication.getPrincipal().equals("anonymousUser")) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
		}

		String memberId = authentication.getName();

		aService.Planlikecancel(planId, memberId);
		return ResponseEntity.ok("좋아요 취소 완료");
	}

	@PostMapping("/favorite/add")
	public ResponseEntity<?> add(@RequestBody Map<String, Object> body) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()
				|| authentication.getPrincipal().equals("anonymousUser")) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
		}

		String memberId = authentication.getName();
		int attractionNo = (int) body.get("attractionNo");
		aService.addFavorite(memberId, attractionNo);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/favorite/remove")
	public ResponseEntity<?> remove(@RequestBody Map<String, Object> body) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()
				|| authentication.getPrincipal().equals("anonymousUser")) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
		}

		String memberId = authentication.getName();
		int attractionNo = (int) body.get("attractionNo");
		aService.removeFavorite(memberId, attractionNo);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/favorite/all")
	public ResponseEntity<List<Map<String, Object>>> getAllFavorites() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()
				|| "anonymousUser".equals(authentication.getPrincipal())) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String memberId = authentication.getName();
		List<Integer> favoriteAttractionNos = aService.getAllFavoriteAttractionNos(memberId);

		List<Map<String, Object>> response = favoriteAttractionNos.stream().map(no -> {
			Map<String, Object> map = new HashMap<>();
			map.put("attractionNo", no);
			return map;
		}).toList();

		return ResponseEntity.ok(response);
	}

	@GetMapping("/plans/liked")
	public ResponseEntity<List<PlanResponse>> getLikedPlans() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| "anonymousUser".equals(authentication.getPrincipal())) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		String memberId = authentication.getName();

		List<PlanResponse> likedPlans = aService.findPlansLikedByMemberId(memberId);

		return ResponseEntity.ok(likedPlans);
	}

	@GetMapping("/my-favorite/all")
	public ResponseEntity<?> findAllFavoriteByMemberId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()
				|| "anonymousUser".equals(authentication.getPrincipal())) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String memberId = authentication.getName();
		List<Integer> favoriteAttractionNos = aService.getAllFavoriteAttractionNos(memberId);
		List<Att> favoriteAttractions = favoriteAttractionNos.stream().map(aService::findAttById)
				.collect(Collectors.toList());

		for (Att att : favoriteAttractions) {
			try {
				double avg = aService.getAvgRating(att.getNo());
				att.setAvgRating(avg);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return ResponseEntity.ok(favoriteAttractions);
	}
}

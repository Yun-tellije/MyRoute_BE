package com.ssafy.ws.controller;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.google.gson.Gson;
import com.ssafy.ws.model.dto.Att;
import com.ssafy.ws.model.dto.Parking;
import com.ssafy.ws.model.service.AttServiceImpl;
import com.ssafy.ws.util.ParkingJsonParser;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import java.io.Reader;
import java.sql.SQLException;
import java.io.FileReader;

@Controller
@RequestMapping("/att")
@RequiredArgsConstructor
public class AttController {
	private final AttServiceImpl aService;

	@GetMapping("/list")
	private String registMemberForm() {
		return "att/att-list-form";
	}

	@PostMapping("/search")
	public String searchAtt(@RequestParam("sido") String sido, @RequestParam("gugun") String gungu,
			@RequestParam("contentType") int contentType, Model model) {
		try {
			List<Att> atts = aService.searchAtt(sido, gungu, contentType);
			model.addAttribute("atts", atts);
			return "att/att-list-form";
		} catch (Exception e) {
			e.printStackTrace();
			model.addAttribute("error", e.getMessage());
			return "att/att-list-form";
		}
	}

	@PostMapping("/search-parking")
	private String searchParking(@RequestParam("lat") double lat, @RequestParam("lng") double lon,
			@RequestParam("name") String name, @RequestParam("id") int id, @RequestParam("image") String first_image1,
			Model model) throws IOException {
		try {

			ClassPathResource resource = new ClassPathResource("/static/resource/parking.json");
			Reader reader = new InputStreamReader(resource.getInputStream());
			List<Parking> allParkings = ParkingJsonParser.parse(reader);
			List<Parking> nearby = findNearbyParkings(lat, lon, allParkings);

			model.addAttribute("parkings", nearby);
			model.addAttribute("lat", lat);
			model.addAttribute("lng", lon);
			model.addAttribute("name", name);
			model.addAttribute("id", id);
			model.addAttribute("image", first_image1);

			return "att/att-list-form";

		} catch (Exception e) {
			e.printStackTrace();
			model.addAttribute("error", "주차장 조회 중 오류 발생: " + e.getMessage());
			return "att/att-list-form";
		}
	}

	public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
		final int R = 6371; // 지구 반지름 km
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
			if (distance <= 1.0) { // 1km 이내만
				p.setDistance(distance);
				filtered.add(p);
			}
		}

		return filtered.stream().sorted(Comparator.comparingDouble(p -> p.distance)).limit(10)
				.collect(Collectors.toList());
	}

	@PostMapping("/attplan")
	private String showAttplan(@RequestParam("sido") String sido, @RequestParam("gugun") String gugun,
			@RequestParam("att_id") int att_id, Model model) throws SQLException {

		List<Att> atts;
		if (att_id == 0) {
			atts = aService.searchAttLocation(sido, gugun);
		} else {
			atts = aService.searchAtt(sido, gugun, att_id);
		}

		model.addAttribute("atts", atts);
		model.addAttribute("selectedAttId", att_id);

		return "att/attplan";
	}

}

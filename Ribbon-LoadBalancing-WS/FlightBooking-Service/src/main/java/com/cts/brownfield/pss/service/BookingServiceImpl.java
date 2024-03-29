package com.cts.brownfield.pss.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.cts.brownfield.pss.controller.Sender;
import com.cts.brownfield.pss.dao.BookingRecordDao;
import com.cts.brownfield.pss.dao.PassengerDao;
import com.cts.brownfield.pss.entity.BookingRecord;
import com.cts.brownfield.pss.entity.Fares;
import com.cts.brownfield.pss.entity.Flight;
import com.cts.brownfield.pss.entity.Passenger;

@EnableFeignClients
@Service
public class BookingServiceImpl implements BookingService {

	@Autowired
	private PassengerDao passengerDao;
	@Autowired
	private BookingRecordDao bookingRecord;
	
	@Autowired
	private Sender sender;
	
	
	@Autowired
	private FareServiceProxy fareServiceProxy;
	@Autowired
	private SearchServiceProxy searchServiceProxy;

	@Override
	public Passenger bookFlight(Passenger passenger, long id, int numberOfPassengers) {

		Fares fare = null;
		Flight flight = null;
		BookingRecord bookingRecord = null;

		try {

			fare=fareServiceProxy.getFare(id);
			System.out.println("======> FARES::::::::: "+fare);
			System.out.println("Fare Searvice Proxy::::: "+fareServiceProxy);
			System.out.println(">>>>>>> :::: SEARCH Service Proxy:::: >>>> "+searchServiceProxy);
			//fare = restTemplate.getForObject(FARESURL + "/getFare/" + id, Fares.class);
			flight=searchServiceProxy.findFlight(id);
			//flight = restTemplate.getForObject(FINDFLIGHT + "/findFlight/" + id, Flight.class);

			System.out.println("=====> Fare: " + fare);
			System.out.println("Flight: " + flight);
		} catch (Exception e) {
			System.out.println("Fares Service is Not Available");
		}

		if (flight.getInventory().getCount() < numberOfPassengers) {
			System.out.println("No More Seats Available");
		}
		if (flight != null) {
			bookingRecord = new BookingRecord(flight.getFlightNumber(), flight.getOrigin(), flight.getDestination(),
					flight.getFlightDate(), flight.getFlightTime(), LocalDateTime.now(), fare.getFare(), "Confirmed");
			// bookingRecord.setPassenger(passenger);
			bookingRecord.setFare(fare.getFare() * numberOfPassengers);
			passenger.setBookingRecord(bookingRecord);
			if (passenger.getCoPassengers().size() == numberOfPassengers - 1) {

				passengerDao.save(passenger);

				
			}
		}

		// Send New Inventory details to Search microservice via RabbitMQ to update new
		// inventory in search schema
		Map<String, Object> bookingDetails = new HashMap<String, Object>();
		bookingDetails.put("FLIGHT_NUMBER", flight.getFlightNumber());
		bookingDetails.put("FLIGHT_DATE", flight.getFlightDate());
		bookingDetails.put("NEW_INVENTORY", numberOfPassengers);

		sender.send(bookingDetails);

		return passenger;
	}

	// The below method is invoked after checked-in to change status from
	// BOOKING_CONFIRMED TO CHECKED_IN
	public void updateStatus(String status, long bookingId) {
		BookingRecord record = bookingRecord.findById(bookingId).orElse(null);
		if (record != null) {
			System.out.println(">>>>> Updating status = " + status + " of bookingId = " + bookingId + " <<<<");
			record.setStatus(status);
			bookingRecord.save(record);
		}
	}

	public BookingRecord getBookingInfo(long bookingId) {

		return bookingRecord.findById(bookingId).orElse(null);
	}

}

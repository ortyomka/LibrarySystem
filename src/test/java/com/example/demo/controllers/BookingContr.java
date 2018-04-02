package com.example.demo.controller;

import com.example.demo.entity.booking.Booking;
import com.example.demo.entity.document.Document;
import com.example.demo.entity.user.User;
import com.example.demo.exception.*;
import com.example.demo.service.*;
import com.example.security.ParserToken;
import com.example.security.TokenAuthenticationService;
import javafx.geometry.Pos;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

@RestController
public class BookingContr {
    private TypeBookingService typeBookingService;
    private BookingService bookingService;
    private DocumentService documentService;
    private UserService userService;
    private NotificationService notificationService;

    private static final long BESTSELLER_FOR_PATRON_TIME = 1209600000L;

    private static final long PATRON_DEFAULT_TIME = 1814400000L;

    private static final long FACULTY_DEFAULT_TIME = 2419200000L;

    private static final long AV_JOURNAL_TIME = 1209600000L;

    private static final long RENEW_TIME = 1209600000L;

    private static final long AVAILABLE_TIME = 86400000L;

    private static final long VP_TIME = 604800000L;


    BookingContr(BookingService bookingService, DocumentService documentService, UserService userService, TypeBookingService typeBookingService, NotificationService notificationService) {
        this.bookingService = bookingService;
        this.documentService = documentService;
        this.userService = userService;
        this.typeBookingService = typeBookingService;
        this.notificationService = notificationService;
    }

    public Iterable<Booking> findBookingByUserId(Integer id) {
        if (id == -1)
            throw new InvalidIdException();
        return bookingService.findAll()
                .stream()
                .filter(booking -> booking.getUser().getId().equals(id))
                .filter(booking -> !("close".equals(booking.getTypeBooking().getTypeName())))
                .collect(Collectors.toList());
    }

    public Iterable<Booking> findMyBooking(Integer userId) {
        return bookingService.findAll()
                .stream()
                .filter(booking -> booking.getUser().getId().equals(userId))
                .filter(booking -> !("close".equals(booking.getTypeBooking().getTypeName()) || "return request".equals(booking.getTypeBooking().getTypeName())))
                .collect(Collectors.toList());
    }

    public Iterable<Booking> findReturnBooks() {
        return bookingService.findAll()
                .stream()
                .filter(booking -> !"close".equals(booking.getTypeBooking().getTypeName()))
                .filter(booking -> "return request".equals(booking.getTypeBooking().getTypeName()))
                .collect(Collectors.toList());
    }

    public Iterable<Booking> findAllBookings() {
        return bookingService.findAll();
    }

    public Iterable<Booking> findAvailableBookings() {
        return bookingService.findAll()
                .stream()
                .filter(booking -> ("available".equals(booking.getTypeBooking().getTypeName())))
                .collect(Collectors.toList());
    }

    public void requestDocumentById(Integer documentId, Integer userId, long currentTime) {
        if (documentId == -1)
            throw new InvalidIdException();
        Document document = documentService.findById(documentId);
        if (document == null)
            throw new DocumentNotFoundException();
        User user = userService.findById(userId);
        if (user == null)
            throw new UserNotFoundException();
        if (!document.isReference()) {
            Date returnDate = new Date();
            if (document.getCount() > 0) {
                long time = currentTime;
                returnDate.setTime(time + AVAILABLE_TIME);
                bookingService.save(new Booking(user, document, returnDate, 0, typeBookingService.findByTypeName("available")));
                document.setCount(document.getCount() - 1);
                documentService.save(document);
            } else {
                bookingService.save(new Booking(user, document, returnDate, 0, typeBookingService.findByTypeName("open")));
            }
        } else {
            throw new AccessDeniedException();
        }
    }

    public void takeDocumentByBookingId(Integer bookingId, long currentTime) {
        if (bookingId == -1)
            throw new InvalidIdException();

        Booking booking = bookingService.getBookingById(bookingId);
        if (booking == null) {
            throw new BookingNotFoundException();
        }

        if (!"available".equals(booking.getTypeBooking().getTypeName())) {
            throw new AccessDeniedException();
        }

        Document document = booking.getDocument();
        User user = booking.getUser();
        Date returnDate = new Date();
        long time = currentTime;
        if (user.getRole().getName().equals("vp")) {
            returnDate.setTime(time + VP_TIME);
        } else if (document.getType().getTypeName().equals("book")) {
            if (user.getRole().getName().equals("patron")) {
                if (document.isBestseller()) {
                    returnDate.setTime(time + BESTSELLER_FOR_PATRON_TIME);
                } else {
                    returnDate.setTime(time + PATRON_DEFAULT_TIME);
                }
            } else if (user.getRole().getName().equals("faculty")) {
                returnDate.setTime(time + FACULTY_DEFAULT_TIME);
            }
        } else {
            returnDate.setTime(time + AV_JOURNAL_TIME);
        }
        booking.setTypeBooking(typeBookingService.findByTypeName("taken"));
        booking.setReturnDate(returnDate);
        bookingService.save(booking);
    }

    public void returnDocumentById(Integer id) {
        if (id == -1)
            throw new InvalidIdException();
        Booking booking = bookingService.getBookingById(id);
        if (booking == null)
            throw new BookingNotFoundException();
        booking.setTypeBooking(typeBookingService.findByTypeName("return request"));
        bookingService.save(booking);
    }

    public void closeBooking(Integer id, long currentTime) {
        if (id == -1)
            throw new InvalidIdException();

        Booking booking = bookingService.getBookingById(id);
        if (booking == null) {
            throw new BookingNotFoundException();
        }
        booking.setTypeBooking(typeBookingService.findByTypeName("close"));
        bookingService.save(booking);


        Document document = booking.getDocument();
        PriorityQueue<Booking> priorityQueue = getQueueForBookById(document.getId());

        if (priorityQueue.size() > 0) {
            booking = priorityQueue.remove();
            booking.setTypeBooking(typeBookingService.findByTypeName("available"));
            Date returnData = new Date();
            returnData.setTime(currentTime + AVAILABLE_TIME);
            booking.setReturnDate(returnData);
            bookingService.save(booking);

            String message = "";
            message += document.getTitle();
            message += " is available for you";
            notificationService.newNotification(booking.getUser().getId(), message);
        } else {
            document.setCount(document.getCount() + 1);
            documentService.save(document);
        }
    }

    public void removeBookingById(Integer id) {
        if (id == -1)
            throw new InvalidIdException();
        this.bookingService.removeBookingById(id);
    }

    public void makeOutstandingRequest(Integer bookingId) {

        if (bookingId == -1)
            throw new InvalidIdException();

        Booking booking = bookingService.getBookingById(bookingId);
        if (booking == null) {
            throw new BookingNotFoundException();
        }

        PriorityQueue<Booking> priorityQueue = getQueueForBookById(booking.getDocument().getId());

        Booking firstBooking = priorityQueue.peek();

        if ("outstanding".equals(firstBooking.getTypeBooking().getTypeName()))
            throw new AlreadyHaveOutstandingRequestException();

        for (Booking bookItem : priorityQueue) {
            bookItem.setTypeBooking(typeBookingService.findByTypeName("close"));
            bookingService.save(bookItem);

            String message = "Your queue position is cancelled";
            notificationService.newNotification(bookItem.getUser().getId(), message);
        }
        booking.setTypeBooking(typeBookingService.findByTypeName("outstanding"));
        bookingService.save(booking);
    }

    public void renewBook(Integer id) {
        if (id == -1)
            throw new InvalidIdException();

        Booking booking = bookingService.getBookingById(id);

        if (booking == null) {
            throw new BookingNotFoundException();
        }

        if (booking.getDocument().isBestseller()) {
            throw new UnableRenewBestsellerException();
        }

        if (!"vp".equals(booking.getUser().getRole().getName()) && "renew".equals(booking.getTypeBooking().getTypeName())) {
            throw new AlreadyRenewException();
        }

        PriorityQueue<Booking> queue = getQueueForBookById(booking.getDocument().getId());

        if (queue.size() > 0 && "outstanding".equals(queue.peek().getTypeBooking().getTypeName())) {
            throw new UnableRenewException();
        }

        booking.setTypeBooking(typeBookingService.findByTypeName("renew"));
        if ("vp".equals(booking.getUser().getRole().getName())) {
            booking.setReturnDate(new Date(booking.getReturnDate().getTime() + VP_TIME));
        } else {
            booking.setReturnDate(new Date(booking.getReturnDate().getTime() + RENEW_TIME));
        }
        bookingService.save(booking);
    }

    public Iterable<Booking> getQueueForBook(Integer id) {
        if (documentService.findById(id) == null) throw new DocumentNotFoundException();

        return getQueueForBookById(id);
    }

    public List<Booking> findActiveBookings() {
        return bookingService.findAll()
                .stream()
                .filter(booking -> ("available".equals(booking.getTypeBooking().getTypeName())
                        || "taken".equals(booking.getTypeBooking().getTypeName())
                        || "renew".equals(booking.getTypeBooking().getTypeName())
                        || "return request".equals(booking.getTypeBooking().getTypeName())))
                .collect(Collectors.toList());
    }

    public void applyMeasures(Booking booking, long currentTime) {
        if ("available".equals(booking.getTypeBooking().getTypeName())) {
            booking.setTypeBooking(typeBookingService.findByTypeName("close"));
            bookingService.save(booking);

            String message = "Your queue position is cancelled";
            notificationService.newNotification(booking.getUser().getId(), message);
            PriorityQueue<Booking> pq = getQueueForBookById(booking.getDocument().getId());
            if (pq.size() > 0) {
                Booking newBooking = pq.peek();
                newBooking.setTypeBooking(typeBookingService.findByTypeName("available"));
                Date returnData = new Date();
                returnData.setTime(currentTime + AVAILABLE_TIME);
                newBooking.setReturnDate(returnData);
                bookingService.save(newBooking);
                message = "";
                message += booking.getDocument().getTitle();
                message += " is available for you";
                notificationService.newNotification(newBooking.getUser().getId(), message);
            }
            else
            {
                Document document = booking.getDocument();
                document.setCount(document.getCount() + 1);
                documentService.save(document);
            }
        } else {
            getFine(booking, currentTime);
            String message = "You have fine of ";
            message += Integer.toString(booking.getFine());
            notificationService.newNotification(booking.getUser().getId(), message);
        }
    }

    private void getFine(Booking booking, long currentTime) {
        Date current = new Date();
        current.setTime(currentTime);

        Document document = booking.getDocument();

        int fine = Math.toIntExact((current.getTime() - booking.getReturnDate().getTime()) / 86400000) * 100;
        if (fine > document.getPrice())
            booking.setFine(document.getPrice());
        else
            booking.setFine(fine);
        bookingService.save(booking);
    }

    public void queueAllocation(Integer bookID) {
        PriorityQueue<Booking> pq = getQueueForBookById(bookID);

        Document document = documentService.findById(bookID);
        Integer countDocument = document.getCount();

        for (int i = 0; i < countDocument && i < pq.size(); i++) {
            Booking booking = pq.poll();
            booking.setTypeBooking(typeBookingService.findByTypeName("available"));
            booking.setReturnDate(new Date(System.currentTimeMillis() + AVAILABLE_TIME));
            bookingService.save(booking);
            String message = "";
            message += booking.getDocument().getTitle();
            message += " is available for you";
            notificationService.newNotification(booking.getUser().getId(), message);
            document.setCount(document.getCount() - 1);
            documentService.save(document);
        }
    }

    public enum Priority {
        PROFESSOR, VP, TA, INSTRUCTOR, STUDENT, OUTSTANDING
    }

    private class MyComparator implements Comparator<Booking> {
        public int compare(Booking x, Booking y) {
            return convertToEnum(y).compareTo(convertToEnum(x));
        }
    }

    private Priority convertToEnum(Booking booking) {
        if ("outstanding".equals(booking.getTypeBooking().getTypeName())) return Priority.OUTSTANDING;
        switch (booking.getUser().getRole().getName().toLowerCase()) {
            case "student":
                return Priority.STUDENT;
            case "instructor":
                return Priority.INSTRUCTOR;
            case "ta":
                return Priority.TA;
            case "professor":
                return Priority.PROFESSOR;
            case "vp":
                return Priority.VP;
        }
        throw new RoleNotFoundException();
    }

    private PriorityQueue<Booking> getQueueForBookById(Integer bookId) {
        PriorityQueue<Booking> queue = new PriorityQueue<>(new MyComparator());

        queue.addAll(bookingService.findAll()
                .stream()
                .filter(booking -> booking.getDocument().getId().equals(bookId))
                .filter(booking -> ("open".equals(booking.getTypeBooking().getTypeName()) || "outstanding".equals(booking.getTypeBooking().getTypeName())))
                .collect(Collectors.toList()));

        return queue;
    }
}

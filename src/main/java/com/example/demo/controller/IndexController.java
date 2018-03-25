package com.example.demo.controller;

import com.example.demo.exception.AccessDeniedException;
import com.example.demo.exception.AlreadyLoginException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.BookingRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.TagRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;
import com.example.security.*;

import javax.servlet.http.HttpServletRequest;

@Controller
public class IndexController {
    public IndexController() {
    }

    @GetMapping("/")
    public ModelAndView login(HttpServletRequest request) {
        ParserToken token = TokenAuthenticationService.getAuthentication(request);
        if (token == null) {
            return new ModelAndView("login");
        }
        throw new AlreadyLoginException();
    }

    @GetMapping("/catalog")
    public ModelAndView catalog(HttpServletRequest request) {
        ParserToken token = TokenAuthenticationService.getAuthentication(request);
        if (token == null) throw new UnauthorizedException();
        if (token.role.equals("admin")) throw new AccessDeniedException();
        return new ModelAndView("catalog");
    }

    @GetMapping("/myBooks")
    public ModelAndView myBooks(HttpServletRequest request) {
        ParserToken token = TokenAuthenticationService.getAuthentication(request);
        if (token == null) throw new UnauthorizedException();
        if (token.role.equals("admin")) throw new AccessDeniedException();
        return new ModelAndView("mybooks");
    }

    @GetMapping("/admin/documents")
    public ModelAndView allDocuments(HttpServletRequest request) {
        ParserToken token = TokenAuthenticationService.getAuthentication(request);
        if (token == null) throw new UnauthorizedException();
        if (!token.role.equals("admin")) throw new AccessDeniedException();
        return new ModelAndView("books");
    }

    @GetMapping("/admin/users")
    public ModelAndView allUsers(HttpServletRequest request) {
        ParserToken token = TokenAuthenticationService.getAuthentication(request);
        if (token == null) throw new UnauthorizedException();
        if (!token.role.equals("admin")) throw new AccessDeniedException();
        return new ModelAndView("users");
    }

    @GetMapping("/admin/requests")
    public ModelAndView allRequests(HttpServletRequest request) {
        ParserToken token = TokenAuthenticationService.getAuthentication(request);
        if (token == null) throw new UnauthorizedException();
        if (!token.role.equals("admin")) throw new AccessDeniedException();
        return new ModelAndView("request");
    }
}

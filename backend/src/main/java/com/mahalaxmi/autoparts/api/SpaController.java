package com.mahalaxmi.autoparts.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {
    @RequestMapping(value = {"/", "/{path:^(?!api|assets|static|.*\\..*$).*$}/**"})
    public String app() {
        return "forward:/index.html";
    }
}

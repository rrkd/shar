package au.com.iglooit.shar.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Created with IntelliJ IDEA.
 * User: nicholas.zhu
 * Date: 19/09/2014
 * Time: 2:25 PM
 */
@Controller
public class HomeController {
    @RequestMapping(value = "/dr/home", method= RequestMethod.GET)
    public String home() {
        return "home";
    }
}

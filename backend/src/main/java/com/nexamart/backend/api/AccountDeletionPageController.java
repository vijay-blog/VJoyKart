package com.nexamart.backend.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccountDeletionPageController {
  @GetMapping("/delete-account")
  String page() { return "redirect:/delete-account/index.html"; }

  @GetMapping("/privacy")
  String privacy() { return "redirect:/privacy/index.html"; }
}

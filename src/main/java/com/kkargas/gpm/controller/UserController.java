package com.kkargas.gpm.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kkargas.gpm.model.WindowsUser;
import com.kkargas.gpm.service.WindowsUserService;


@Controller
public class UserController {

    private final WindowsUserService
            windowsUserService;


    public UserController(
            WindowsUserService windowsUserService) {

        this.windowsUserService =
                windowsUserService;
    }
    
    /*
     * =========================================================
     * CREATE USER PAGE
     * =========================================================
     */

    @GetMapping("/users/create")
    public String createUserPage() {

        return "CreateUser";
    }
    
    /*
     * =========================================================
     * CREATE LOCAL USER
     * =========================================================
     */

    @PostMapping("/users/create")
    public String createUser(
            @RequestParam String username,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String description,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam(
                    defaultValue = "false"
            ) boolean administrator,
            @RequestParam(
                    defaultValue = "false"
            ) boolean userMayChangePassword,
            @RequestParam(
                    defaultValue = "false"
            ) boolean passwordRequired) {


        /*
         * Validate username
         */

        if (username == null
                || username.isBlank()) {

            return "redirect:/users/create"
                    + "?usernameError=true";
        }


        /*
         * Validate password
         */

        if (password == null
                || password.isBlank()
                || !password.equals(confirmPassword)) {

            return "redirect:/users/create"
                    + "?passwordError=true";
        }


        /*
         * Check if user already exists
         */

        if (windowsUserService
                .getLocalUser(username) != null) {

            return "redirect:/users/create"
                    + "?userExists=true";
        }


        /*
         * Create Windows account
         */

        windowsUserService
                .createUser(
                        username,
                        fullName,
                        description,
                        password,
                        administrator,
                        userMayChangePassword,
                        passwordRequired
                );


        return "redirect:/users/"
                + username
                + "?created=true";
    }


    /*
     * =========================================================
     * USERS PAGE
     * =========================================================
     */

    @GetMapping("/users")
    public String viewUsers() {
        // Render the page immediately; Windows account discovery happens separately.
        return "Users";
    }

    @GetMapping("/users/data")
    public String usersData(Model model) {
        List<WindowsUser> users = windowsUserService.getLocalUsers();
        model.addAttribute("users", users);
        model.addAttribute("totalUsers", users.size());
        model.addAttribute("enabledUsers", users.stream().filter(WindowsUser::isEnabled).count());
        model.addAttribute("administratorCount", users.stream().filter(WindowsUser::isAdministrator).count());
        return "fragments/users-data :: content";
    }

    @GetMapping("/users/{username}")
    public String viewUser(
            @PathVariable String username,
            Model model) {

        WindowsUser user =
                windowsUserService.getLocalUser(username);

        if (user == null) {
            return "redirect:/users";
        }

        model.addAttribute(
                "user",
                user
        );

        return "UserPage";
    }
    
    /*
     * =========================================================
     * UPDATE USER INFORMATION
     * =========================================================
     */

    @PostMapping("/users/{username}/update")
    public String updateUser(
            @PathVariable String username,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String description) {

        WindowsUser user =
                windowsUserService
                        .getLocalUser(username);

        if (user == null) {
            return "redirect:/users";
        }


        windowsUserService
                .updateUserInformation(
                        username,
                        fullName,
                        description
                );


        return "redirect:/users/" + username;
    }
    
    /*
     * =========================================================
     * CHANGE USER PASSWORD
     * =========================================================
     */

    @PostMapping("/users/{username}/password")
    public String changePassword(
            @PathVariable String username,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword) {

        WindowsUser user =
                windowsUserService
                        .getLocalUser(username);


        if (user == null) {
            return "redirect:/users";
        }


        if (newPassword == null
                || newPassword.isBlank()
                || !newPassword.equals(confirmPassword)) {

            return "redirect:/users/" + username
                    + "?passwordError=true";
        }


        windowsUserService
                .changePassword(
                        username,
                        newPassword
                );


        return "redirect:/users/" + username
                + "?passwordChanged=true";
    }
    
    /*
     * =========================================================
     * UPDATE USER SETTINGS
     * =========================================================
     */

    @PostMapping("/users/{username}/settings")
    public String updateUserSettings(
            @PathVariable String username,
            @RequestParam(
                    defaultValue = "false"
            ) boolean enabled,
            @RequestParam(
                    defaultValue = "false"
            ) boolean administrator,
            @RequestParam(
                    defaultValue = "false"
            ) boolean userMayChangePassword,
            @RequestParam(
                    defaultValue = "false"
            ) boolean passwordRequired) {


        WindowsUser user =
                windowsUserService
                        .getLocalUser(username);


        if (user == null) {
            return "redirect:/users";
        }


        windowsUserService
                .updateUserSettings(
                        username,
                        enabled,
                        administrator,
                        userMayChangePassword,
                        passwordRequired
                );


        return "redirect:/users/" + username
                + "?settingsUpdated=true";
    }
    /* =========================================================
     * DELETE LOCAL USER
     * ========================================================= */

    @PostMapping("/users/{username}/delete")
    public String deleteUser(
            @PathVariable String username,
            @RequestParam String confirmation,
            RedirectAttributes redirectAttributes) {

        if (!username.equals(confirmation)) {
            redirectAttributes.addFlashAttribute(
                    "deleteError", "The confirmation username does not match.");
            return "redirect:/users/" + username;
        }

        try {
            windowsUserService.deleteLocalUser(username);
            redirectAttributes.addFlashAttribute(
                    "deleteSuccess", "The local Windows account was deleted successfully. Its profile files were not removed.");
            return "redirect:/users";
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "deleteError", "Account deletion failed. Check account protection and administrator permissions.");
            return "redirect:/users/" + username;
        }
    }

}
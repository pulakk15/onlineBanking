package com.bankapp;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        BankingService service = new BankingService();
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("\n=== SIMPLE ONLINE BANKING APP ===");
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Choose: ");

            int choice = Integer.parseInt(sc.nextLine());

            try {
                if (choice == 1) {
                    System.out.print("Full Name: ");
                    String name = sc.nextLine();

                    System.out.print("Email: ");
                    String email = sc.nextLine();

                    System.out.print("Password: ");
                    String password = sc.nextLine();

                    User user = service.register(name, email, password);
                    System.out.println("User registered successfully. ID = " + user.getId());
                } else if (choice == 2) {
                    System.out.print("Email: ");
                    String email = sc.nextLine();

                    System.out.print("Password: ");
                    String password = sc.nextLine();

                    User user = service.login(email, password);

                    if (user == null) {
                        System.out.println("Invalid email or password.");
                    } else {
                        System.out.println("Welcome, " + user.getFullName() + "!");
                        userMenu(sc, service, user);
                    }
                } else if (choice == 3) {
                    System.out.println("Goodbye!");
                    break;
                } else {
                    System.out.println("Invalid choice.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        sc.close();
    }

    private static void userMenu(Scanner sc, BankingService service, User user) {
        while (true) {
            System.out.println("\n=== USER MENU ===");
            System.out.println("1. Create Account");
            System.out.println("2. View My Accounts");
            System.out.println("3. View Balance");
            System.out.println("4. Deposit");
            System.out.println("5. Withdraw");
            System.out.println("6. Transfer");
            System.out.println("7. Transaction History");
            System.out.println("8. Logout");
            System.out.print("Choose: ");

            int choice = Integer.parseInt(sc.nextLine());

            try {
                switch (choice) {
                    case 1 -> {
                        String acc = service.createAccount(user.getId());
                        System.out.println("Account created successfully.");
                        System.out.println("Account Number: " + acc);
                    }
                    case 2 -> {
                        List<String> accounts = service.getUserAccounts(user.getId());
                        if (accounts.isEmpty()) {
                            System.out.println("No accounts found.");
                        } else {
                            System.out.println("\nYour Accounts:");
                            for (String a : accounts) {
                                System.out.println(a);
                            }
                        }
                    }
                    case 3 -> {
                        System.out.print("Account Number: ");
                        String acc = sc.nextLine();
                        BigDecimal balance = service.getBalance(acc);
                        if (balance == null) {
                            System.out.println("Account not found.");
                        } else {
                            System.out.println("Balance: ₹" + balance);
                        }
                    }
                    case 4 -> {
                        System.out.print("Account Number: ");
                        String acc = sc.nextLine();

                        System.out.print("Amount: ");
                        BigDecimal amt = new BigDecimal(sc.nextLine());

                        service.deposit(acc, amt);
                        System.out.println("Deposit successful.");
                    }
                    case 5 -> {
                        System.out.print("Account Number: ");
                        String acc = sc.nextLine();

                        System.out.print("Amount: ");
                        BigDecimal amt = new BigDecimal(sc.nextLine());

                        service.withdraw(acc, amt);
                        System.out.println("Withdrawal successful.");
                    }
                    case 6 -> {
                        System.out.print("From Account: ");
                        String from = sc.nextLine();

                        System.out.print("To Account: ");
                        String to = sc.nextLine();

                        System.out.print("Amount: ");
                        BigDecimal amt = new BigDecimal(sc.nextLine());

                        service.transfer(from, to, amt);
                        System.out.println("Transfer successful.");
                    }
                    case 7 -> {
                        System.out.print("Account Number: ");
                        String acc = sc.nextLine();
                        service.printTransactionHistory(acc);
                    }
                    case 8 -> {
                        System.out.println("Logged out.");
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
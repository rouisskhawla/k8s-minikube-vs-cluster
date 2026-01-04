package com.devops.k8s.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.devops.k8s.entity.Customer;
import com.devops.k8s.service.CustomerService;

import java.util.List;


@RestController
@RequestMapping("/api/v1/customers")
public class Controller {

   @Autowired
   private CustomerService customerService;

    // Create a new customer
    @PostMapping("/create")
    public Customer createCustomer(@RequestBody Customer customer) {
        return customerService.saveCustomer(customer);
    }

    // Retrieve all customers
    @GetMapping("/all")
    public List<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    // Retrieve a specific customer by ID
    @GetMapping("/{customerId}")
    public Customer getCustomerById(@PathVariable int customerId) {
        return customerService.findById(customerId);
    }

}

package com.app.shahbaztrades.validator;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.util.DateUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderValidator {

    public static void validateOrderDate(LocalDate orderDate) {
        var threshold = orderDate.atTime(9, 0).atZone(DateUtil.IST_ZONE);
        var now = ZonedDateTime.now(DateUtil.IST_ZONE);
        if (now.isAfter(threshold)) {
            throw new BadRequestException("Order cannot be placed/edited for this date");
        }
    }

    public static void validateForDelete(Instant orderDate) {
        var orderInIst = orderDate.atZone(DateUtil.IST_ZONE);
        var start = orderInIst.withHour(9).withMinute(0).withSecond(0).withNano(0);
        var end = orderInIst.withHour(15).withMinute(30).withSecond(0).withNano(0);
        var now = Instant.now().atZone(DateUtil.IST_ZONE);
        if (now.isBefore(end) && now.isAfter(start)) {
            throw new BadRequestException("Order cannot be deleted at this time please try later.");
        }
    }

    public static void validateForCreateAndUpdate(User user, BrokerType brokerType) {
        switch (brokerType) {
            case ZERODHA -> {
                if (!BrokerConfigValidator.validateZerodhaConfig(user.getZerodhaConfig())) {
                    throw new BadRequestException("Zerodha is not registered");
                }
            }
            case RUPEEZY -> {
                if (!BrokerConfigValidator.validateRupeezyConfig(user.getRupeezyConfig())) {
                    throw new BadRequestException("Rupeezy is not registered");
                }
            }
            default -> throw new BadRequestException("Broker not supported");
        }
    }

}

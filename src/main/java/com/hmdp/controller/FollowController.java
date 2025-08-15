package com.hmdp.controller;


import com.hmdp.dto.Result;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId)
}

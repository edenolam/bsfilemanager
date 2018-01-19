package com.iorga.cig.bs.FileStorageManager.filters;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@WebFilter(urlPatterns = "/*")
public class IpAccessFilter implements Filter {

    @Override
    public void init (FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter (ServletRequest request,
                          ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String url = request instanceof HttpServletRequest ?
                ((HttpServletRequest) request).getRequestURL().toString() : "N/A";
        System.out.println("======> from filter, processing url: "+url);
        chain.doFilter(request, response);
    }

    @Override
    public void destroy () {

    }
}
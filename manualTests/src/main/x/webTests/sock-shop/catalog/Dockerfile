FROM gradle:jdk17

COPY . /xvm

RUN cd /xvm \
    && gradle compileOne -PtestName=sock-shop/socks-catalog \
    && gradle compileOne -PtestName=sock-shop/socks-catalog-api \
    && rm -rf /xvm/manualTests/build/SockShopCatalog_data \
    && rm /xvm/manualTests/src/main/x/sock-shop/catalog/application.json \
    && cp /xvm/manualTests/src/main/x/sock-shop/catalog/application-prod.json /xvm/manualTests/src/main/x/sock-shop/catalog/application.json

WORKDIR /xvm

EXPOSE 8080
EXPOSE 80

ENTRYPOINT ["gradle", "-Dxvm.db.impl=json", "hostOne", "-PtestName=SockShopCatalogApi"]
FROM node:20-alpine

# Create app directory
WORKDIR /usr/src/app

# Install app dependencies
# A wildcard is used to ensure both package.json AND package-lock.json are copied
COPY package*.json ./

RUN npm ci --only=production

# Bundle app source (includes data/food_items.json)
COPY . .

# Set the food data path for the containerized environment
ENV FOOD_DATA_PATH=/usr/src/app/data/food_items.json

# Expose port (Cloud Run sets PORT env var)
EXPOSE 8080

# Start command
CMD [ "node", "src/server.js" ]

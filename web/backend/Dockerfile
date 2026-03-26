FROM node:20-alpine

# Create app directory
WORKDIR /usr/src/app

# Install app dependencies
# A wildcard is used to ensure both package.json AND package-lock.json are copied
COPY package*.json ./

RUN npm ci --only=production

# Bundle app source
COPY . .

# Expose port (Cloud Run sets PORT env var)
EXPOSE 8080

# Start command
CMD [ "node", "src/server.js" ]
